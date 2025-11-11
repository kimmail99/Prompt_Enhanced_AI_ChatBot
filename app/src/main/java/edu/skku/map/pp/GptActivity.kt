package edu.skku.map.pp

import java.io.IOException
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
import kotlinx.coroutines.*
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.*
import org.json.JSONObject

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

    private val client by lazy { OkHttpClient() }

    private val db = FirebaseFirestore.getInstance()
    private val userId = FirebaseAuth.getInstance().currentUser?.uid ?: "guest"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_gpt)

        tvUserName = findViewById(R.id.tvUserName)
        rvChat = findViewById(R.id.rvChat)
        etUserQuestion = findViewById(R.id.etUserQuestion)
        btnSendQuestion = findViewById(R.id.btnSendQuestion)
        btnPromptEngineering = findViewById(R.id.btnPromptEngineering)
        btnHistory = findViewById(R.id.btnHistory)
        btnSave = findViewById(R.id.btnSave)

        val currentUser = FirebaseAuth.getInstance().currentUser
        val displayName = currentUser?.displayName ?: currentUser?.email ?: "사용자"
        tvUserName.text = displayName

        chatAdapter = ChatAdapter(chatList)
        rvChat.layoutManager = LinearLayoutManager(this)
        rvChat.adapter = chatAdapter

        val docId = intent.getStringExtra("docId")
        if (!docId.isNullOrEmpty()) restoreConversationFromFirestore(docId)

        btnHistory.setOnClickListener {
            startActivity(Intent(this, HistoryActivity::class.java))
        }

        btnSave.setOnClickListener { saveConversation() }

        // 단일 프롬프트로 도메인+의도+정제 수행
        btnPromptEngineering.setOnClickListener {
            val userQuestion = etUserQuestion.text.toString().trim()
            if (userQuestion.isNotEmpty()) {
                lifecycleScope.launch {
                    val refined = refineQuestionAsync(userQuestion)
                    if (refined.isNotEmpty()) {
                        // 마지막 단락(정제된 질문)만 EditText에 반영
                        val paragraphs = refined.split("\n\n")
                        val finalRefined = paragraphs.lastOrNull()?.trim() ?: refined
                        etUserQuestion.setText(finalRefined)
                    } else {
                        Toast.makeText(this@GptActivity, "정제 실패. 원문 유지.", Toast.LENGTH_SHORT).show()
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

    private fun restoreConversationFromFirestore(docId: String) {
        db.collection("conversations")
            .document(docId)
            .get()
            .addOnSuccessListener { doc ->
                if (doc.exists()) {
                    val conv = doc.getString("conversation") ?: return@addOnSuccessListener
                    chatList.clear()

                    // "User:" 또는 "GPT:"로 분리
                    val segments = conv.split(Regex("(?=User: |GPT: )")).map { it.trim() }
                    for (seg in segments) {
                        when {
                            seg.startsWith("User: ") -> chatList.add(ChatMessage(seg.removePrefix("User: ").trim(), true))
                            seg.startsWith("GPT: ") -> chatList.add(ChatMessage(seg.removePrefix("GPT: ").trim(), false))
                        }
                    }

                    chatAdapter.notifyDataSetChanged()
                    rvChat.scrollToPosition(chatList.size - 1)
                }
            }
            .addOnFailureListener { e ->
                Log.e("GptActivity", "Firestore restore error", e)
            }
    }

    // 한 번의 GPT 요청으로 도메인/의도 => 정제 수행
    private suspend fun refineQuestionAsync(originalQuestion: String): String = withContext(Dispatchers.IO) {
        try {
            val prompt = """
            You are a fast and concise refinement assistant.
            The user will ask a question.

            Please rewrite the user question regarding steps:
            • First: briefly describe the domain or topic of this question under 20 words.
            • Second: briefly describe the user's intent or purpose under 20 words.
            • Refine the question clearly and enclose it entirely within double quotes (" ").

            ---
            Question: "$originalQuestion"
            ---

            Do NOT include any numbering, bullets, or additional explanations.
        """.trimIndent()

            val refined = callGptApi(prompt)
            Log.d("GptActivity", "Refined full output:\n$refined")
            refined
        } catch (e: Exception) {
            Log.e("GptActivity", "refineQuestionAsync error", e)
            ""
        }
    }

    // 공통 GPT 호출 함수 (에러/JSON 보정 포함)
    private suspend fun callGptApi(prompt: String): String = suspendCancellableCoroutine { cont ->
        try {
            val requestBodyJson = """
                {
                  "model": "gpt-3.5-turbo",
                  "messages": [
                    {"role": "system", "content": "You are a helpful assistant."},
                    {"role": "user", "content": ${JSONObject.quote(prompt)}}
                  ],
                  "temperature": 0.7
                }
            """.trimIndent()

            val body = RequestBody.create("application/json; charset=utf-8".toMediaTypeOrNull(), requestBodyJson)
            val request = Request.Builder()
                .url("https://api.openai.com/v1/chat/completions")
                .addHeader("Authorization", "Bearer ${BuildConfig.OPENAI_API_KEY}")
                .post(body)
                .build()

            client.newCall(request).enqueue(object : Callback {
                override fun onFailure(call: Call, e: IOException) {
                    Log.e("GptActivity", "API call failed: ${e.message}")
                    if (cont.isActive) cont.resume("") {}
                }

                override fun onResponse(call: Call, response: Response) {
                    try {
                        val bodyString = response.body?.string()
                        Log.d("GptActivity", "GPT raw: $bodyString")
                        if (response.isSuccessful && !bodyString.isNullOrEmpty()) {
                            val result = parseChatCompletion(bodyString)
                            if (cont.isActive) cont.resume(result ?: "") {}
                        } else {
                            Log.e("GptActivity", "GPT HTTP error: ${response.code}")
                            if (cont.isActive) cont.resume("") {}
                        }
                    } catch (ex: Exception) {
                        Log.e("GptActivity", "onResponse parse error", ex)
                        if (cont.isActive) cont.resume("") {}
                    }
                }
            })
        } catch (e: Exception) {
            Log.e("GptActivity", "callGptApi exception", e)
            if (cont.isActive) cont.resume("") {}
        }
    }

    // GPT에게 메시지 전송 (대화 기억 포함 recent 10 chats)
    private suspend fun sendMessageToGptAsync(question: String): String = withContext(Dispatchers.IO) {
        try {
            // 최근 대화 포함 (너무 길면 앞부분 생략)
            val recentHistory = if (chatList.size > 10) chatList.takeLast(10) else chatList

            // messages 배열 구성 (system + user/assistant 이력 + 새로운 user 질문)
            val messagesJson = buildString {
                append("""{"role":"system","content":"You are a helpful and context-aware assistant."},""")
                recentHistory.forEach { msg ->
                    val role = if (msg.isUser) "user" else "assistant"
                    append("""{"role":"$role","content":${org.json.JSONObject.quote(msg.message)}},""")
                }
                append("""{"role":"user","content":${org.json.JSONObject.quote(question)}}""")
            }

            val requestBodyJson = """
            {
              "model": "gpt-3.5-turbo",
              "messages": [ $messagesJson ],
              "temperature": 0.7
            }
        """.trimIndent()

            val body = RequestBody.create("application/json; charset=utf-8".toMediaTypeOrNull(), requestBodyJson)
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
            } else {
                Log.e("GptActivity", "GPT HTTP error: ${response.code}")
                "오류: HTTP ${response.code}"
            }

        } catch (e: Exception) {
            Log.e("GptActivity", "sendMessageToGptAsync error", e)
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
