package com.example.znakiai

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView

data class ChatMessage(val message: String, val isUser: Boolean)

class ChatAdapter : RecyclerView.Adapter<ChatAdapter.ChatViewHolder>() {

    private val messages: MutableList<ChatMessage> = mutableListOf()

    fun addMessage(message: ChatMessage) {
        messages.add(message)
        notifyItemInserted(messages.size - 1)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ChatViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_chat_message, parent, false)
        return ChatViewHolder(view)
    }

    override fun onBindViewHolder(holder: ChatViewHolder, position: Int) {
        val chatMessage = messages[position]
        holder.bind(chatMessage)
    }

    override fun getItemCount(): Int = messages.size

    class ChatViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val messageTextView: TextView = itemView.findViewById(R.id.message_text_view)

        fun bind(chatMessage: ChatMessage) {
            messageTextView.text = chatMessage.message
            if (chatMessage.isUser) {
                messageTextView.setBackgroundResource(R.drawable.user_message_background)
            } else {
                messageTextView.setBackgroundResource(R.drawable.assistant_message_background)
            }
        }
    }
}
