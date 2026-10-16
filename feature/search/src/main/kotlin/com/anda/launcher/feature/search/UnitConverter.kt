package com.anda.launcher.feature.search

/** Offline unit conversion for search queries like "5 km to mi", "72f in c" or "3 cups". */
object UnitConverter {

    private enum class Dimension { Length, Mass, Volume, Speed, Data, Time, Temperature }

    private data class Unit(val symbol: String, val dimension: Dimension, val toBase: Double, val aliases: List<String>)

    private val units = listOf(
        Unit("mm", Dimension.Length, 0.001, listOf("millimeter", "millimeters")),
        Unit("cm", Dimension.Length, 0.01, listOf("centimeter", "centimeters")),
        Unit("m", Dimension.Length, 1.0, listOf("meter", "meters", "metre", "metres")),
        Unit("km", Dimension.Length, 1000.0, listOf("kilometer", "kilometers", "kilometre")),
        Unit("in", Dimension.Length, 0.0254, listOf("inch", "inches", "\"")),
        Unit("ft", Dimension.Length, 0.3048, listOf("foot", "feet", "'")),
        Unit("yd", Dimension.Length, 0.9144, listOf("yard", "yards")),
        Unit("mi", Dimension.Length, 1609.344, listOf("mile", "miles")),
        Unit("mg", Dimension.Mass, 0.001, listOf("milligram", "milligrams")),
        Unit("g", Dimension.Mass, 1.0, listOf("gram", "grams")),
        Unit("kg", Dimension.Mass, 1000.0, listOf("kilogram", "kilograms", "kilo", "kilos")),
        Unit("oz", Dimension.Mass, 28.349523125, listOf("ounce", "ounces")),
        Unit("lb", Dimension.Mass, 453.59237, listOf("lbs", "pound", "pounds")),
        Unit("st", Dimension.Mass, 6350.29318, listOf("stone", "stones")),
        Unit("ml", Dimension.Volume, 0.001, listOf("milliliter", "milliliters")),
        Unit("l", Dimension.Volume, 1.0, listOf("liter", "liters", "litre", "litres")),
        Unit("tsp", Dimension.Volume, 0.00492892, listOf("teaspoon", "teaspoons")),
        Unit("tbsp", Dimension.Volume, 0.0147868, listOf("tablespoon", "tablespoons")),
        Unit("floz", Dimension.Volume, 0.0295735, listOf("fl oz", "fluid ounce")),
        Unit("cup", Dimension.Volume, 0.236588, listOf("cups")),
        Unit("gal", Dimension.Volume, 3.78541, listOf("gallon", "gallons")),
        Unit("km/h", Dimension.Speed, 1 / 3.6, listOf("kmh", "kph")),
        Unit("mph", Dimension.Speed, 0.44704, listOf()),
        Unit("m/s", Dimension.Speed, 1.0, listOf("mps")),
        Unit("kn", Dimension.Speed, 0.514444, listOf("knot", "knots")),
        Unit("B", Dimension.Data, 1.0, listOf("byte", "bytes")),
        Unit("KB", Dimension.Data, 1e3, listOf("kilobyte", "kilobytes")),
        Unit("MB", Dimension.Data, 1e6, listOf("megabyte", "megabytes")),
        Unit("GB", Dimension.Data, 1e9, listOf("gigabyte", "gigabytes")),
        Unit("TB", Dimension.Data, 1e12, listOf("terabyte", "terabytes")),
        Unit("s", Dimension.Time, 1.0, listOf("sec", "second", "seconds")),
        Unit("min", Dimension.Time, 60.0, listOf("minute", "minutes")),
        Unit("h", Dimension.Time, 3600.0, listOf("hr", "hour", "hours")),
        Unit("day", Dimension.Time, 86400.0, listOf("days", "d")),
        Unit("week", Dimension.Time, 604800.0, listOf("weeks", "wk")),
        Unit("°C", Dimension.Temperature, 1.0, listOf("c", "celsius")),
        Unit("°F", Dimension.Temperature, 1.0, listOf("f", "fahrenheit")),
        Unit("K", Dimension.Temperature, 1.0, listOf("k", "kelvin")),
    )

    private val lookup: Map<String, Unit> = buildMap {
        // Data units are case-sensitive in spirit (MB vs mb both mean megabytes here).
        for (u in units) {
            put(u.symbol.lowercase(), u)
            u.aliases.forEach { put(it.lowercase(), u) }
        }
    }

    private val defaultTargets = mapOf(
        Dimension.Length to listOf("m", "km", "mi", "ft"),
        Dimension.Mass to listOf("kg", "lb", "g", "oz"),
        Dimension.Volume to listOf("l", "ml", "cup", "gal"),
        Dimension.Speed to listOf("km/h", "mph", "m/s"),
        Dimension.Data to listOf("MB", "GB", "KB"),
        Dimension.Time to listOf("min", "h", "s", "day"),
        Dimension.Temperature to listOf("°C", "°F", "K"),
    )

    private val pattern = Regex("""^\s*(-?\d+(?:[.,]\d+)?)\s*([a-z°/'"]+(?:\s?[a-z]+)?)\s*(?:(?:to|in|as|=)\s*([a-z°/'"]+(?:\s?[a-z]+)?))?\s*$""", RegexOption.IGNORE_CASE)

    /** Returns lines like "5 km = 3.10686 mi", or empty if the query isn't a conversion. */
    fun convert(query: String): List<String> {
        val match = pattern.find(query) ?: return emptyList()
        val value = match.groupValues[1].replace(',', '.').toDoubleOrNull() ?: return emptyList()
        val from = lookup[match.groupValues[2].trim().lowercase()] ?: return emptyList()
        val explicitTarget = match.groupValues[3].takeIf { it.isNotBlank() }
        val targets = if (explicitTarget != null) {
            listOfNotNull(lookup[explicitTarget.trim().lowercase()]).filter { it.dimension == from.dimension }
        } else {
            defaultTargets[from.dimension].orEmpty().mapNotNull { lookup[it.lowercase()] }.filter { it != from }.take(3)
        }
        return targets.map { to ->
            val result = convert(value, from, to)
            "${ExpressionEvaluator.format(value)} ${from.symbol} = ${ExpressionEvaluator.format(round(result))} ${to.symbol}"
        }
    }

    private fun convert(value: Double, from: Unit, to: Unit): Double {
        if (from.dimension != Dimension.Temperature) return value * from.toBase / to.toBase
        val celsius = when (from.symbol) {
            "°F" -> (value - 32) * 5 / 9
            "K" -> value - 273.15
            else -> value
        }
        return when (to.symbol) {
            "°F" -> celsius * 9 / 5 + 32
            "K" -> celsius + 273.15
            else -> celsius
        }
    }

    private fun round(value: Double): Double = Math.round(value * 1e6) / 1e6
}
