package com.example.znakiai

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.znakiai.databinding.FragmentAiAssistantBinding

class AIAssistantFragment : Fragment() {
    private lateinit var binding: FragmentAiAssistantBinding
    private lateinit var chatAdapter: ChatAdapter
    private lateinit var ollamaClient: OllamaClient

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        binding = FragmentAiAssistantBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // Inicjalizacja klienta Ollama
        ollamaClient = OllamaClient("http://localhost:8080")

        // Inicjalizacja adaptera czatu
        chatAdapter = ChatAdapter()
        binding.chatRecyclerView.apply {
            layoutManager = LinearLayoutManager(requireContext())
            adapter = chatAdapter
        }

        // Obsługa przycisku wysyłania wiadomości
        binding.sendButton.setOnClickListener {
            val message = binding.messageEditText.text.toString().trim()
            if (message.isNotEmpty()) {
                sendMessage(message)
                binding.messageEditText.text.clear()
            }
        }
    }

    private fun sendMessage(message: String) {
        // Dodaj wiadomość użytkownika do czatu
        chatAdapter.addMessage(ChatMessage(message, true))

        // Wyślij zapytanie do asystenta Ollama
        ollamaClient.sendRequest(message) { response ->
            // Dodaj odpowiedź asystenta do czatu
            requireActivity().runOnUiThread {
                chatAdapter.addMessage(ChatMessage(response, false))
            }
        }
    }
}
