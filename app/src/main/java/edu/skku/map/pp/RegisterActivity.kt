package edu.skku.map.pp

import android.os.Bundle
import android.util.Log
import android.widget.Button
import android.widget.EditText
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore

class RegisterActivity : AppCompatActivity() {
    private lateinit var etNameRegister: EditText
    private lateinit var etEmailRegister: EditText
    private lateinit var etPasswordRegister: EditText
    private lateinit var btnRegister: Button

    private lateinit var auth: FirebaseAuth
    private val db = FirebaseFirestore.getInstance()

    companion object {
        private const val TAG = "RegisterActivity"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_register)
        etNameRegister = findViewById(R.id.etNameRegister)
        etEmailRegister = findViewById(R.id.etEmailRegister)
        etPasswordRegister = findViewById(R.id.etPasswordRegister)
        btnRegister = findViewById(R.id.btnRegister)
        auth = FirebaseAuth.getInstance()

        Log.d(TAG, "onCreate: RegisterActivity launched")
        btnRegister.setOnClickListener {
            val name = etNameRegister.text.toString().trim()
            val email = etEmailRegister.text.toString().trim()
            val password = etPasswordRegister.text.toString().trim()

            if (name.isEmpty() || email.isEmpty() || password.isEmpty()) {
                Toast.makeText(this, "모든 항목을 입력하세요.", Toast.LENGTH_SHORT).show()
            } else {
                registerUser(name, email, password)
            }
        }
    }
    private fun registerUser(name: String, email: String, password: String) {

        auth.createUserWithEmailAndPassword(email, password)
            .addOnCompleteListener { task ->
                if (task.isSuccessful) {
                    val user = auth.currentUser
                    val uid = user?.uid
                    Log.d(TAG, "currentUser uid=$uid")

                    if (uid != null) {
                        val userInfo = hashMapOf(
                            "name" to name,
                            "email" to email
                        )
                        Toast.makeText(this, "회원가입 성공!", Toast.LENGTH_SHORT).show()
                        db.collection("users").document(uid).set(userInfo)
                            .addOnSuccessListener {
                                finish() // 회원가입 액티비티 종료
                            }
                            .addOnFailureListener { e ->
                                Toast.makeText(this, "데이터 저장 실패: ${e.message}", Toast.LENGTH_SHORT).show()
                            }
                    } else {
                        Log.w(TAG, "uid is null after auth success? Something unexpected happened.")
                        Toast.makeText(this, "회원가입은 됐지만 사용자 정보를 불러오지 못했습니다.", Toast.LENGTH_SHORT).show()
                    }
                } else {
                    val errorMsg = task.exception?.message
                    Log.e(TAG, "createUserWithEmailAndPassword: failure, msg=$errorMsg", task.exception)
                    Toast.makeText(this, "회원가입 실패: $errorMsg", Toast.LENGTH_SHORT).show()
                }
                finish()
            }
    }
}
