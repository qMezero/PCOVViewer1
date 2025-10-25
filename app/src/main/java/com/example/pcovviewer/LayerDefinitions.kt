package com.example.pcovviewer

import android.content.Context
import androidx.annotation.StringRes

data class LayerCodeDefinition(val code: String, @StringRes val nameRes: Int)

data class LayerGroupDefinition(@StringRes val titleRes: Int, val codes: List<LayerCodeDefinition>)

object LayerDefinitions {

    val groups: List<LayerGroupDefinition> = listOf(
        LayerGroupDefinition(
            R.string.layers_group_contours,
            listOf(
                LayerCodeDefinition("10", R.string.layers_code_10),
                LayerCodeDefinition("11", R.string.layers_code_11),
                LayerCodeDefinition("12", R.string.layers_code_12),
                LayerCodeDefinition("15", R.string.layers_code_15),
                LayerCodeDefinition("16", R.string.layers_code_16),
                LayerCodeDefinition("17", R.string.layers_code_17),
                LayerCodeDefinition("18", R.string.layers_code_18),
                LayerCodeDefinition("30", R.string.layers_code_30),
                LayerCodeDefinition("32", R.string.layers_code_32),
                LayerCodeDefinition("33", R.string.layers_code_33),
                LayerCodeDefinition("35", R.string.layers_code_35),
                LayerCodeDefinition("36", R.string.layers_code_36),
                LayerCodeDefinition("37", R.string.layers_code_37),
                LayerCodeDefinition("38", R.string.layers_code_38),
                LayerCodeDefinition("39", R.string.layers_code_39),
                LayerCodeDefinition("49", R.string.layers_code_49),
                LayerCodeDefinition("200", R.string.layers_code_200),
                LayerCodeDefinition("210", R.string.layers_code_210),
                LayerCodeDefinition("220", R.string.layers_code_220),
                LayerCodeDefinition("230", R.string.layers_code_230),
                LayerCodeDefinition("240", R.string.layers_code_240),
                LayerCodeDefinition("270", R.string.layers_code_270),
                LayerCodeDefinition("271", R.string.layers_code_271),
                LayerCodeDefinition("280", R.string.layers_code_280),
                LayerCodeDefinition("301", R.string.layers_code_301),
                LayerCodeDefinition("306", R.string.layers_code_306),
                LayerCodeDefinition("991", R.string.layers_code_991),
                LayerCodeDefinition("992", R.string.layers_code_992),
                LayerCodeDefinition("993", R.string.layers_code_993),
                LayerCodeDefinition("994", R.string.layers_code_994),
                LayerCodeDefinition("995", R.string.layers_code_995)
            )
        ),
        LayerGroupDefinition(
            R.string.layers_group_subway,
            listOf(
                LayerCodeDefinition("40", R.string.layers_code_40),
                LayerCodeDefinition("41", R.string.layers_code_41),
                LayerCodeDefinition("43", R.string.layers_code_43),
                LayerCodeDefinition("45", R.string.layers_code_45)
            )
        ),
        LayerGroupDefinition(
            R.string.layers_group_greenery,
            listOf(
                LayerCodeDefinition("70", R.string.layers_code_70),
                LayerCodeDefinition("73", R.string.layers_code_73),
                LayerCodeDefinition("702", R.string.layers_code_702),
                LayerCodeDefinition("703", R.string.layers_code_703),
                LayerCodeDefinition("704", R.string.layers_code_704),
                LayerCodeDefinition("705", R.string.layers_code_705),
                LayerCodeDefinition("706", R.string.layers_code_706),
                LayerCodeDefinition("731", R.string.layers_code_731)
            )
        ),
        LayerGroupDefinition(
            R.string.layers_group_fences,
            listOf(
                LayerCodeDefinition("50", R.string.layers_code_50),
                LayerCodeDefinition("51", R.string.layers_code_51),
                LayerCodeDefinition("52", R.string.layers_code_52),
                LayerCodeDefinition("53", R.string.layers_code_53),
                LayerCodeDefinition("54", R.string.layers_code_54),
                LayerCodeDefinition("55", R.string.layers_code_55),
                LayerCodeDefinition("511", R.string.layers_code_511),
                LayerCodeDefinition("531", R.string.layers_code_531)
            )
        ),
        LayerGroupDefinition(
            R.string.layers_group_poles,
            listOf(
                LayerCodeDefinition("60", R.string.layers_code_60),
                LayerCodeDefinition("63", R.string.layers_code_63),
                LayerCodeDefinition("67", R.string.layers_code_67),
                LayerCodeDefinition("68", R.string.layers_code_68),
                LayerCodeDefinition("80", R.string.layers_code_80),
                LayerCodeDefinition("81", R.string.layers_code_81),
                LayerCodeDefinition("84", R.string.layers_code_84),
                LayerCodeDefinition("86", R.string.layers_code_86),
                LayerCodeDefinition("601", R.string.layers_code_601),
                LayerCodeDefinition("602", R.string.layers_code_602),
                LayerCodeDefinition("612", R.string.layers_code_612)
            )
        ),
        LayerGroupDefinition(
            R.string.layers_group_railway,
            listOf(
                LayerCodeDefinition("38", R.string.layers_code_38),
                LayerCodeDefinition("39", R.string.layers_code_39),
                LayerCodeDefinition("381", R.string.layers_code_381),
                LayerCodeDefinition("653", R.string.layers_code_653),
                LayerCodeDefinition("655", R.string.layers_code_655),
                LayerCodeDefinition("824", R.string.layers_code_824),
                LayerCodeDefinition("841", R.string.layers_code_841)
            )
        ),
        LayerGroupDefinition(
            R.string.layers_group_hydrography,
            listOf(
                LayerCodeDefinition("90", R.string.layers_code_90),
                LayerCodeDefinition("96", R.string.layers_code_96)
            )
        )
    )

    private val codeDefinitions: Map<String, LayerCodeDefinition> = groups
        .flatMap { it.codes }
        .associateBy { it.code }

    fun codeDisplayName(context: Context, code: String): String? {
        val definition = codeDefinitions[code] ?: return null
        return context.getString(definition.nameRes)
    }

    fun isKnownCode(code: String): Boolean = codeDefinitions.containsKey(code)
}

object LayerOrdering {
    val comparator: Comparator<String?> = Comparator { first, second ->
        if (first == null && second == null) {
            0
        } else if (first == null) {
            -1
        } else if (second == null) {
            1
        } else {
            val firstInt = first.toIntOrNull()
            val secondInt = second.toIntOrNull()
            when {
                firstInt != null && secondInt != null -> firstInt.compareTo(secondInt)
                firstInt != null -> -1
                secondInt != null -> 1
                else -> first.compareTo(second)
            }
        }
    }
}
