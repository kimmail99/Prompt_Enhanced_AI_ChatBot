package edu.skku.map.pp

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.gson.JsonParser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody

class GptActivity : AppCompatActivity() {

    private lateinit var tvUserName: TextView
    private lateinit var rvChat: RecyclerView
    private lateinit var etUserQuestion: EditText

    private lateinit var btnSendQuestion: Button
    private lateinit var btnPromptEngineering: Button
    private lateinit var btnHistory: Button
    private lateinit var btnSave: Button

    private lateinit var chatAdapter: ChatAdapter
    private val chatList = mutableListOf<ChatMessage>()

    // Firestore
    private val db = FirebaseFirestore.getInstance()
    private val userId = FirebaseAuth.getInstance().currentUser?.uid ?: "guest"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_gpt)

        tvUserName         = findViewById(R.id.tvUserName)
        rvChat             = findViewById(R.id.rvChat)
        etUserQuestion     = findViewById(R.id.etUserQuestion)
        btnSendQuestion    = findViewById(R.id.btnSendQuestion)
        btnPromptEngineering = findViewById(R.id.btnPromptEngineering)
        btnHistory         = findViewById(R.id.btnHistory)
        btnSave            = findViewById(R.id.btnSave)

        val currentUser = FirebaseAuth.getInstance().currentUser
        val displayName = currentUser?.displayName ?: currentUser?.email ?: "사용자"
        tvUserName.text = displayName

        chatAdapter = ChatAdapter(chatList)
        rvChat.layoutManager = LinearLayoutManager(this)
        rvChat.adapter = chatAdapter

        // HistoryActivity에서 전달된 docId가 있으면 해당 대화 복구
        val docId = intent.getStringExtra("docId")
        if (!docId.isNullOrEmpty()) {
            restoreConversationFromFirestore(docId)
        }

        btnHistory.setOnClickListener {
            startActivity(Intent(this, HistoryActivity::class.java))
        }

        btnSave.setOnClickListener {
            saveConversation()
        }

        btnPromptEngineering.setOnClickListener {
            val userQuestion = etUserQuestion.text.toString().trim()
            if (userQuestion.isNotEmpty()) {
                lifecycleScope.launch {
                    val refined = refineQuestionAsync(userQuestion)
                    if (refined.isNotEmpty()) {
                        etUserQuestion.setText(refined)
                    }
                }
            }
        }

        btnSendQuestion.setOnClickListener {
            val userQuestion = etUserQuestion.text.toString().trim()
            if (userQuestion.isNotEmpty()) {
                val userMsg = ChatMessage(userQuestion, isUser = true)
                chatList.add(userMsg)
                chatAdapter.notifyItemInserted(chatList.size - 1)
                rvChat.scrollToPosition(chatList.size - 1)

                etUserQuestion.setText("")

                lifecycleScope.launch {
                    val gptResponse = sendMessageToGptAsync(userQuestion)

                    val gptMsg = ChatMessage(gptResponse, isUser = false)
                    chatList.add(gptMsg)
                    chatAdapter.notifyItemInserted(chatList.size - 1)
                    rvChat.scrollToPosition(chatList.size - 1)
                }
            }
        }
    }

    // Firestore에 대화 저장
    private fun saveConversation() {
        val entireConversationText = buildString {
            chatList.forEach { msg ->
                if (msg.isUser) append("User: ${msg.message}\n")
                else append("GPT: ${msg.message}\n")
            }
        }

        val data = hashMapOf(
            "userId" to userId,
            "timestamp" to System.currentTimeMillis(),
            "conversation" to entireConversationText
        )

        db.collection("conversations")
            .add(data)
            .addOnSuccessListener { Log.d("GptActivity", "Saved to Firestore") }
            .addOnFailureListener { e -> Log.e("GptActivity", "Error saving", e) }

        Toast.makeText(this, "대화가 저장되었습니다.", Toast.LENGTH_SHORT).show()
    }

    // Firestore에서 대화 복구
    private fun restoreConversationFromFirestore(docId: String) {
        db.collection("conversations")
            .document(docId)
            .get()
            .addOnSuccessListener { doc ->
                if (doc.exists()) {
                    val conv = doc.getString("conversation") ?: return@addOnSuccessListener
                    chatList.clear()
                    conv.split("\n").forEach { line ->
                        if (line.startsWith("User: ")) chatList.add(ChatMessage(line.removePrefix("User: "), true))
                        else if (line.startsWith("GPT: ")) chatList.add(ChatMessage(line.removePrefix("GPT: "), false))
                    }
                    chatAdapter.notifyDataSetChanged()
                    rvChat.scrollToPosition(chatList.size - 1)
                }
            }
            .addOnFailureListener { e -> Log.e("GptActivity", "Firestore restore error", e) }
    }

    // GPT API 호출
    private suspend fun refineQuestionAsync(originalQuestion: String): String = withContext(Dispatchers.IO) {
        try {
            val requestBodyJson = """
                {
                  "model": "gpt-3.5-turbo",
                  "messages": [
                    {"role": "system","content": "You are a refinement assistant. Analyze the question step by step but output only the refined version."},
                    {"role": "user","content": "Q: $originalQuestion"}
                  ],
                  "temperature": 0.7
                }
            """.trimIndent()

            val client = OkHttpClient()
            val mediaType = "application/json; charset=utf-8".toMediaTypeOrNull()
            val body = RequestBody.create(mediaType, requestBodyJson)

            val request = Request.Builder()
                .url("https://api.openai.com/v1/chat/completions")
                .addHeader("Authorization", "Bearer ${BuildConfig.OPENAI_API_KEY}")
                .post(body)
                .build()

            val response = client.newCall(request).execute()
            if (response.isSuccessful) {
                val responseBody = response.body?.string()
                if (!responseBody.isNullOrEmpty()) parseChatCompletion(responseBody) ?: ""
                else ""
            } else ""
        } catch (e: Exception) {
            Log.e("GptActivity", "Refine request exception", e)
            ""
        }
    }

    private suspend fun sendMessageToGptAsync(question: String): String = withContext(Dispatchers.IO) {
        try {
            val requestBodyJson = """
                {
                  "model": "gpt-3.5-turbo",
                  "messages": [
                    {"role": "system", "content": "You are a helpful assistant."},
                    {"role": "user",   "content": "$question"}
                  ],
                  "temperature": 0.7
                }
            """.trimIndent()

            val client = OkHttpClient()
            val mediaType = "application/json; charset=utf-8".toMediaTypeOrNull()
            val body = RequestBody.create(mediaType, requestBodyJson)
            val request = Request.Builder()
                .url("https://api.openai.com/v1/chat/completions")
                .addHeader("Authorization", "Bearer ${BuildConfig.OPENAI_API_KEY}")
                .post(body)
                .build()

            val response = client.newCall(request).execute()
            if (response.isSuccessful) {
                val responseBody = response.body?.string()
                if (responseBody.isNullOrEmpty()) return@withContext "GPT 응답이 비어있습니다."
                parseChatCompletion(responseBody) ?: "GPT 응답 파싱 실패"
            } else "오류: HTTP ${response.code}"
        } catch (e: Exception) {
            "오류 발생: ${e.message}"
        }
    }

    private fun parseChatCompletion(jsonString: String): String {
        return try {
            val jsonObj = JsonParser.parseString(jsonString).asJsonObject
            val choicesArr = jsonObj.getAsJsonArray("choices")
            if (choicesArr.size() > 0) {
                val messageObj = choicesArr[0].asJsonObject.getAsJsonObject("message")
                messageObj.get("content").asString.trim()
            } else ""
        } catch (e: Exception) {
            Log.e("GptActivity", "parseChatCompletion error", e)
            ""
        }
    }
}
