package com.emrekutlug.wassup5

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.widget.Button
import android.widget.ProgressBar
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import com.emrekutlug.wassup5.model.WassupMessage
import com.firebase.ui.database.FirebaseRecyclerOptions
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInClient
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.ktx.auth
import com.emrekutlug.wassup5.databinding.ActivityMainBinding
import com.google.firebase.database.DatabaseReference
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ktx.database
import com.google.firebase.ktx.Firebase
import com.google.firebase.storage.StorageReference
import com.google.firebase.storage.ktx.storage
import kotlinx.android.synthetic.main.activity_sign_in.*

class MainActivity : AppCompatActivity() {
    private lateinit var signInClient: GoogleSignInClient
    private lateinit var binding: ActivityMainBinding
    private lateinit var manager: LinearLayoutManager

    private lateinit var auth: FirebaseAuth
    private lateinit var db: FirebaseDatabase
    private lateinit var adapter: WassupMessageAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        auth = Firebase.auth
        if (auth.currentUser == null) {
            startActivity(Intent(this, SignInActivity::class.java))
            finish()
            return
        }

        val gso = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
            .requestIdToken(getString(R.string.default_web_client_id))
            .requestEmail()
            .build()
        signInClient = GoogleSignIn.getClient(this, gso)

        db = Firebase.database
        val messagesRef = db.reference.child(MESSAGES_CHILD)

        val options = FirebaseRecyclerOptions.Builder<WassupMessage>()
            .setQuery(messagesRef, WassupMessage::class.java)
            .build()
        adapter = WassupMessageAdapter(options, getUserName())
        binding.progressBar.visibility = ProgressBar.INVISIBLE
        manager = LinearLayoutManager(this)
        manager.stackFromEnd = true
        binding.messageRecyclerView.layoutManager = manager
        binding.messageRecyclerView.adapter = adapter

        adapter.registerAdapterDataObserver(
            MyScrollToBottomObserver(binding.messageRecyclerView, adapter, manager)
        )

        binding.messageEditText.addTextChangedListener(MyButtonObserver(binding.sendButton))

        binding.sendButton.setOnClickListener {
            val wassupMessage = WassupMessage(
                binding.messageEditText.text.toString(),
                getUserName(),
                getPhotoUrl(),
                null
            )
            db.reference.child(MESSAGES_CHILD).push().setValue(wassupMessage)
            binding.messageEditText.setText("")
        }

        binding.addMessageImageView.setOnClickListener {
            val intent = Intent(Intent.ACTION_OPEN_DOCUMENT)
            intent.addCategory(Intent.CATEGORY_OPENABLE)
            intent.type = "image/*"
            startActivityForResult(intent, REQUEST_IMAGE)
        }
    }

    public override fun onStart() {
        super.onStart()
        if (auth.currentUser == null) {
            startActivity(Intent(this, SignInActivity::class.java))
            finish()
            return
        }
    }

    public override fun onPause() {
        adapter.stopListening()
        super.onPause()
    }

    public override fun onResume() {
        super.onResume()
        adapter.startListening()
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        val inflater = menuInflater
        inflater.inflate(R.menu.main_menu, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            R.id.sign_out_menu -> {
                signOut()
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        Log.d(TAG, "onActivityResult: requestCode=$requestCode, resultCode=$resultCode")
        if (requestCode == REQUEST_IMAGE) {
            if (resultCode == RESULT_OK && data != null) {
                val uri = data.data
                Log.d(TAG, "Uri: " + uri.toString())
                val user = auth.currentUser
                val tempMessage =
                    WassupMessage(null, getUserName(), getPhotoUrl(), LOADING_IMAGE_URL)
                db.reference.child(MESSAGES_CHILD).push()
                    .setValue(
                        tempMessage,
                        DatabaseReference.CompletionListener { databaseError, databaseReference ->
                            if (databaseError != null) {
                                Log.w(
                                    TAG, "Unable to write message to database.",
                                          databaseError.toException()
                                      )
                                      return@CompletionListener
                                  }

                                  val key = databaseReference.key
                                  val storageReference = Firebase.storage
                                      .getReference(user!!.uid)
                                      .child(key!!)
                                      .child(uri!!.lastPathSegment!!)
                                  putImageInStorage(storageReference, uri, key)
                              })
            }
        }
    }

    private fun putImageInStorage(storageReference: StorageReference, uri: Uri, key: String?) {
        storageReference.putFile(uri)
            .addOnSuccessListener(
                this
            ) { taskSnapshot ->
                taskSnapshot.metadata!!.reference!!.downloadUrl
                    .addOnSuccessListener { uri ->
                        val WassMessage =
                            WassupMessage(null, getUserName(), getPhotoUrl(), uri.toString())
                        db.reference
                            .child(MESSAGES_CHILD)
                            .child(key!!)
                            .setValue(WassMessage)
                    }
            }
            .addOnFailureListener(this) { e ->
                Log.w(
                    TAG,
                    "Image upload task was unsuccessful.",
                    e
                )
            }
    }

    private fun signOut() {
        auth.signOut()
        signInClient.signOut()
        startActivity(Intent(this, SignInActivity::class.java))
        finish()
    }

    private fun getPhotoUrl(): String? {
        val user = auth.currentUser
        return user?.photoUrl?.toString()
    }

    private fun getUserName(): String? {
        val user = auth.currentUser
        return if (user != null) {
            user.displayName
        } else ANONYMOUS
    }



    companion object {
        private const val TAG = "MainActivity"
        const val MESSAGES_CHILD = "messages"
        const val ANONYMOUS = "anonymous"
        private const val REQUEST_IMAGE = 2
        private const val LOADING_IMAGE_URL = "https://www.google.com/images/spin-32.gif"
    }
}