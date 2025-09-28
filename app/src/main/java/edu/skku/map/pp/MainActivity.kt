package edu.skku.map.pp

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.widget.Button
import android.widget.EditText
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInAccount
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.gms.common.SignInButton
import com.google.android.gms.common.api.ApiException
import com.google.android.gms.tasks.Task
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.GoogleAuthProvider

class MainActivity : AppCompatActivity() {

    private lateinit var btnGoogleSignIn: SignInButton
    private lateinit var btnLogoutGoogle: Button
    private lateinit var btnLoginEmail: Button
    private lateinit var btnGoRegister: Button

    private lateinit var etEmail: EditText
    private lateinit var etPassword: EditText

    private lateinit var auth: FirebaseAuth

    private lateinit var googleSignInClient: com.google.android.gms.auth.api.signin.GoogleSignInClient

    companion object {
        private const val RC_SIGN_IN = 100
        private const val TAG = "MainActivity"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_main)

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        auth = FirebaseAuth.getInstance()

        etEmail = findViewById(R.id.etEmail)
        etPassword = findViewById(R.id.etPassword)
        btnLoginEmail = findViewById(R.id.btnLoginEmail)
        btnGoRegister = findViewById(R.id.btnGoRegister)

        btnGoogleSignIn = findViewById(R.id.btn_google_sign_in)
        btnLogoutGoogle = findViewById(R.id.btn_logout_google)

        btnLoginEmail.setOnClickListener {
            val email = etEmail.text.toString().trim()
            val password = etPassword.text.toString().trim()
            if (email.isEmpty() || password.isEmpty()) {
                showToast("이메일/비밀번호를 입력하세요.")
            } else {
                emailLogin(email, password)
            }
        }

        btnGoRegister.setOnClickListener {
            startActivity(Intent(this, RegisterActivity::class.java))
        }

        val gso = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
            .requestIdToken(getString(R.string.default_web_client_id))
            .requestEmail()
            .build()
        googleSignInClient = GoogleSignIn.getClient(this, gso)

        val account = GoogleSignIn.getLastSignedInAccount(this)
        if (account != null) {
            showToast("이미 구글 계정으로 로그인된 상태입니다:\n${account.email}")
        }

        btnGoogleSignIn.setOnClickListener {
            signInWithGoogle()
        }

        btnLogoutGoogle.setOnClickListener {
            signOutGoogle()
        }
    }

    private fun emailLogin(email: String, password: String) {
        auth.signInWithEmailAndPassword(email, password)
            .addOnCompleteListener { task ->
                if (task.isSuccessful) {
                    showToast("이메일 로그인 성공! 환영합니다: $email")
                    startActivity(Intent(this, GptActivity::class.java))
                    finish()
                } else {
                    showToast("로그인 실패: 이메일/비밀번호를 확인하세요")
                }
            }
    }
    private fun signInWithGoogle() {
        val signInIntent = googleSignInClient.signInIntent
        startActivityForResult(signInIntent, RC_SIGN_IN)
    }
    private fun signOutGoogle() {
        auth.signOut()
        googleSignInClient.signOut().addOnCompleteListener {
            showToast("구글 로그아웃 완료")
        }
    }
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == RC_SIGN_IN) {
            val task = GoogleSignIn.getSignedInAccountFromIntent(data)
            handleSignInResult(task)
        }
    }

    private fun handleSignInResult(completedTask: Task<GoogleSignInAccount>) {
        try {
            val account = completedTask.getResult(ApiException::class.java)
            if (account != null) {
                firebaseAuthWithGoogle(account)
            }
        } catch (e: ApiException) {
            Log.w(TAG, "Google sign in failed", e)
            showToast("구글 로그인 실패: ${e.message}")
        }
    }
    private fun firebaseAuthWithGoogle(acct: GoogleSignInAccount) {
        val credential = GoogleAuthProvider.getCredential(acct.idToken, null)
        auth.signInWithCredential(credential)
            .addOnCompleteListener(this) { task ->
                if (task.isSuccessful) {
                    val user = auth.currentUser
                    showToast("구글 로그인 성공! 환영합니다: ${user?.email}")
                    startActivity(Intent(this, GptActivity::class.java))
                    finish()
                } else {
                    showToast("구글 로그인 실패: ${task.exception?.message}")
                }
            }
    }
    private fun showToast(message: String) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
    }
}
