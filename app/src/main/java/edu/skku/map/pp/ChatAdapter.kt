package edu.skku.map.pp

import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView

class ChatAdapter(private val chatList: List<ChatMessage>) : RecyclerView.Adapter<ChatAdapter.ChatViewHolder>() {

    inner class ChatViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val layoutBubble: LinearLayout = itemView.findViewById(R.id.layoutBubble)
        val tvMessage: TextView = itemView.findViewById(R.id.tvMessage)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ChatViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_chat_message, parent, false)
        return ChatViewHolder(view)
    }

    override fun onBindViewHolder(holder: ChatViewHolder, position: Int) {
        val chatMessage = chatList[position]
        holder.tvMessage.text = chatMessage.message

        if (chatMessage.isUser) {
            holder.layoutBubble.gravity = Gravity.END
            holder.tvMessage.setBackgroundResource(R.drawable.bubble_user)
        } else {
            holder.layoutBubble.gravity = Gravity.START
            holder.tvMessage.setBackgroundResource(R.drawable.bubble_gpt)
        }
    }

    override fun getItemCount(): Int = chatList.size
}

