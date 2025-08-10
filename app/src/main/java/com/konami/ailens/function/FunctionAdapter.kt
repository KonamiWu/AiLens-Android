package com.konami.ailens.function

import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.navigation.NavController
import androidx.recyclerview.widget.RecyclerView
import androidx.recyclerview.widget.RecyclerView.ViewHolder
import com.konami.ailens.ble.AiLens
import com.konami.ailens.ble.BLEService
import com.konami.ailens.R
import com.konami.ailens.function.FunctionAdapter.FunctionViewHolder

class FunctionAdapter(aiLens: AiLens, navController: NavController): RecyclerView.Adapter<FunctionViewHolder>() {

    class FunctionViewHolder(itemView: View): ViewHolder(itemView) {
        val titleTextView: TextView = itemView.findViewById(R.id.titleTextView)
    }

    private class SimpleActionItem(
        override val title: String,
        val action: () -> Unit
    ) : ListItem {
        override fun execute() = action()
    }

    private val commands = mutableListOf<ListItem>()

    init {
        val session = BLEService.instance.getSession(aiLens.device.address)
        if (session != null) {
            commands.add(OpenCanvasListItem(session))
            commands.add(ClearCanvasListItem(session))
            commands.add(CloseCanvasListItem(session))
            commands.add(DrawRectListItem(session, x = 0, y = 0, width = 639, height = 479, lineWidth = 1, fill = false))
            commands.add(SubscribeIMUListItem(session))
            commands.add(UnSubscribeIMUListItem(session))
            commands.add(FaceDetectionListItem(navController))
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): FunctionViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.function_item, parent, false)
        return FunctionViewHolder(view)
    }

    override fun onBindViewHolder(holder: FunctionViewHolder, position: Int) {
        holder.titleTextView.text = commands[position].title
        holder.itemView.setOnClickListener {
            commands[position].execute()
        }
    }

    override fun getItemCount(): Int = commands.size
}