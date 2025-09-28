package edu.skku.map.pp

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.widget.Button
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore

class HistoryActivity : AppCompatActivity() {

    private lateinit var rvHistory: RecyclerView
    private lateinit var adapter: HistoryAdapter
    private val historyList = mutableListOf<HistoryItem>()

    private lateinit var btnClearHistory: Button

    private val db = FirebaseFirestore.getInstance()
    private val userId = FirebaseAuth.getInstance().currentUser?.uid ?: "guest"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_history)

        rvHistory = findViewById(R.id.rvHistory)
        adapter = HistoryAdapter(historyList) { clicked ->
            val intent = Intent(this, GptActivity::class.java)
            intent.putExtra("docId", clicked.fileName) // Firestore 문서 ID 넘김
            startActivity(intent)
            finish()
        }
        rvHistory.layoutManager = LinearLayoutManager(this)
        rvHistory.adapter = adapter

        btnClearHistory = findViewById(R.id.btnClearHistory)
        btnClearHistory.setOnClickListener {
            clearHistory()
        }

        loadHistoryList()
    }

    // Firestore에서 목록 불러오기
    private fun loadHistoryList() {
        db.collection("conversations")
            .whereEqualTo("userId", userId)
            .orderBy("timestamp", com.google.firebase.firestore.Query.Direction.DESCENDING)
            .get()
            .addOnSuccessListener { docs ->
                historyList.clear()
                for (doc in docs) {
                    val docId = doc.id
                    val timestamp = doc.getLong("timestamp") ?: 0L
                    historyList.add(HistoryItem(docId, timestamp))
                }
                adapter.notifyDataSetChanged()
                if (historyList.isEmpty()) {
                    Toast.makeText(this, "저장된 대화 없음", Toast.LENGTH_SHORT).show()
                }
            }
            .addOnFailureListener { e ->
                Log.e("HistoryActivity", "loadHistoryList error: $e")
                Toast.makeText(this, "히스토리 로드 실패: ${e.message}", Toast.LENGTH_SHORT).show()
            }
    }

    // Firestore의 모든 히스토리 삭제
    private fun clearHistory() {
        db.collection("conversations")
            .whereEqualTo("userId", userId)
            .get()
            .addOnSuccessListener { docs ->
                for (doc in docs) {
                    db.collection("conversations").document(doc.id).delete()
                }
                historyList.clear()
                adapter.notifyDataSetChanged()
                Toast.makeText(this, "히스토리 목록을 삭제했습니다.", Toast.LENGTH_SHORT).show()
            }
            .addOnFailureListener { e ->
                Toast.makeText(this, "삭제 중 오류: ${e.message}", Toast.LENGTH_SHORT).show()
            }
    }
}

data class HistoryItem(
    val fileName: String,
    val timestamp: Long
)
