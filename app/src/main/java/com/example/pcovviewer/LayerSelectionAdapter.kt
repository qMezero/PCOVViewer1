package com.example.pcovviewer

import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.BaseExpandableListAdapter
import android.widget.CheckBox
import android.widget.TextView

class LayerSelectionAdapter(
    private val context: Context,
    private val groups: List<LayerSelectionGroup>,
    private val selectionState: MutableMap<String?, Boolean>
) : BaseExpandableListAdapter() {

    private val layoutInflater: LayoutInflater = LayoutInflater.from(context)

    override fun getGroupCount(): Int = groups.size

    override fun getChildrenCount(groupPosition: Int): Int = groups[groupPosition].items.size

    override fun getGroup(groupPosition: Int): LayerSelectionGroup = groups[groupPosition]

    override fun getChild(groupPosition: Int, childPosition: Int): LayerSelectionItem =
        groups[groupPosition].items[childPosition]

    override fun getGroupId(groupPosition: Int): Long = groupPosition.toLong()

    override fun getChildId(groupPosition: Int, childPosition: Int): Long =
        ((groupPosition shl 16) or childPosition).toLong()

    override fun hasStableIds(): Boolean = false

    override fun getGroupView(
        groupPosition: Int,
        isExpanded: Boolean,
        convertView: View?,
        parent: ViewGroup?
    ): View {
        val view = convertView ?: layoutInflater.inflate(R.layout.item_layer_group, parent, false)
        val titleView = view.findViewById<TextView>(R.id.layerGroupTitle)
        titleView.text = getGroup(groupPosition).title
        return view
    }

    override fun getChildView(
        groupPosition: Int,
        childPosition: Int,
        isLastChild: Boolean,
        convertView: View?,
        parent: ViewGroup?
    ): View {
        val view = convertView ?: layoutInflater.inflate(R.layout.item_layer_code, parent, false)
        val checkBox = view.findViewById<CheckBox>(R.id.layerCheckBox)
        val item = getChild(groupPosition, childPosition)

        checkBox.setOnCheckedChangeListener(null)
        checkBox.text = context.getString(
            R.string.layers_dialog_item_format,
            item.displayName,
            item.count
        )
        checkBox.isChecked = selectionState[item.baseCode] ?: false
        checkBox.setOnCheckedChangeListener { _, isChecked ->
            selectionState[item.baseCode] = isChecked
        }

        return view
    }

    override fun isChildSelectable(groupPosition: Int, childPosition: Int): Boolean = true
}

data class LayerSelectionGroup(
    val title: String,
    val items: List<LayerSelectionItem>
)

data class LayerSelectionItem(
    val baseCode: String?,
    val displayName: String,
    val count: Int
)
