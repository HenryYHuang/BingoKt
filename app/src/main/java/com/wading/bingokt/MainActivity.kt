package com.wading.bingokt

import android.content.Intent
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import androidx.appcompat.app.AlertDialog
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.firebase.ui.auth.AuthUI
import com.firebase.ui.database.FirebaseRecyclerAdapter
import com.firebase.ui.database.FirebaseRecyclerOptions
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.*
import kotlinx.android.synthetic.main.activity_main.*
import kotlinx.android.synthetic.main.room_row.view.*

class MainActivity : AppCompatActivity(), FirebaseAuth.AuthStateListener, View.OnClickListener {
    companion object {
        const val RC_SIGN_IN = 100
        val TAG = MainActivity::class.java.simpleName
    }

    private lateinit var adapter: FirebaseRecyclerAdapter<GameRoom, RoomHolder>
    private var member: Member? = null
    val avatars = intArrayOf(R.drawable.avatar_0,
        R.drawable.avatar_1,R.drawable.avatar_2,
        R.drawable.avatar_3, R.drawable.avatar_4, R.drawable.avatar_5)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        group_avatars.visibility = View.GONE
        horizon_scroll.visibility = View.GONE
        name_txt.setOnClickListener {
            showNicknameDialog(FirebaseAuth.getInstance().uid!!
            , name_txt.text.toString())
        }

        user_image.setOnClickListener {
            group_avatars.visibility = if (group_avatars.visibility == View.GONE) View.VISIBLE else View.GONE
            horizon_scroll.visibility = if (group_avatars.visibility == View.GONE) View.VISIBLE else View.GONE
        }

        avatar_0.setOnClickListener(this)
        avatar_1.setOnClickListener(this)
        avatar_2.setOnClickListener(this)
        avatar_3.setOnClickListener(this)
        avatar_4.setOnClickListener(this)
        avatar_5.setOnClickListener(this)

        fab.setOnClickListener {
            val editText = EditText(this)
            editText.setText("Welcome")
            AlertDialog.Builder(this).setTitle("Room title")
                .setMessage("Enter a room title")
                .setView(editText)
                .setPositiveButton("OK") { _, _ ->
                    FirebaseDatabase.getInstance().getReference("rooms")
                        .push()
                        .setValue(GameRoom(editText.text.toString(), member),
                            object : DatabaseReference.CompletionListener {
                                override fun onComplete(error: DatabaseError?, databaseReference: DatabaseReference) {
                                    val intent = Intent(this@MainActivity, BingoActivity::class.java)
                                    val roomId = databaseReference.key
                                    FirebaseDatabase.getInstance().getReference("rooms")
                                        .child(roomId!!)
                                        .child("id")
                                        .setValue(roomId)
                                    intent.putExtra("ROOM_ID", databaseReference.key)
                                    intent.putExtra("IS_CREATE", true)
                                    startActivity(intent)
                                }
                            })
                }.show()
        }

        recycler.setHasFixedSize(true)
        recycler.layoutManager = LinearLayoutManager(this)

        val query = FirebaseDatabase.getInstance().getReference("rooms")
            .limitToLast(30)
        val options = FirebaseRecyclerOptions.Builder<GameRoom>()
            .setQuery(query, GameRoom::class.java)
            .build()

        adapter =  object : FirebaseRecyclerAdapter<GameRoom, RoomHolder>(options) {
            override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RoomHolder {
                val view = layoutInflater.inflate(R.layout.room_row, parent, false)
                return RoomHolder(view)
            }

            override fun onBindViewHolder(holder: RoomHolder, position: Int, model: GameRoom) {
                holder.title.text = model.title
                holder.image.setImageResource(avatars[model.init!!.avatarId])
                holder.itemView.isEnabled = model.status == BingoActivity.STATUS_CREATED

                if (holder.itemView.isEnabled) {
                    holder.status.text = "等待對手中"
                    holder.status.setTextColor(ContextCompat.getColor(this@MainActivity, R.color.colorBlack))
                } else {
                    holder.status.text = "對戰中"
                    holder.status.setTextColor(ContextCompat.getColor(this@MainActivity, R.color.colorRed))
                }
                holder.itemView.setOnClickListener {
                    val intent = Intent(this@MainActivity, BingoActivity::class.java)
                    intent.putExtra("ROOM_ID", model.id)
                    startActivity(intent)
                }
            }
        }

        recycler.adapter = adapter
    }

    class RoomHolder(view: View): RecyclerView.ViewHolder(view) {
        var title = view.room_title
        var image = view.room_img
        var status = view.room_status
    }

    override fun onStart() {
        super.onStart()
        FirebaseAuth.getInstance().addAuthStateListener(this)
        adapter.startListening()
    }

    override fun onStop() {
        super.onStop()
        FirebaseAuth.getInstance().removeAuthStateListener(this)
        adapter.stopListening()
    }

    override fun onCreateOptionsMenu(menu: Menu?): Boolean {
        menuInflater.inflate(R.menu.menu_main, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        when(item.itemId) {
            R.id.action_signout -> FirebaseAuth.getInstance().signOut()
        }
        return super.onOptionsItemSelected(item)
    }

    override fun onAuthStateChanged(auth: FirebaseAuth) {
        auth.currentUser?.also {
            FirebaseDatabase.getInstance().getReference("users")
                .child(it.uid)
                .addValueEventListener(object : ValueEventListener {
                    override fun onCancelled(databaseError: DatabaseError) {

                    }

                    override fun onDataChange(dataSnapshot: DataSnapshot) {
                        val m = dataSnapshot.getValue(Member::class.java)
                        member = m
                        m?.nickname?.also { nick ->
                            name_txt.setText(nick)
                        }
                        m?.avatarId?.also {
                            user_image.setImageResource(avatars[it])
                        }
                    }
                })

            FirebaseDatabase.getInstance().getReference("users")
                .child(it.uid)
                .child("uid")
                .setValue(it.uid)

            FirebaseDatabase.getInstance().getReference("users")
                .child(it.uid)
                .child("displayName")
                .setValue(it.displayName)

            FirebaseDatabase.getInstance().getReference("users")
                .child(it.uid)
                .child("nickname")
                .addListenerForSingleValueEvent(object : ValueEventListener {
                    override fun onCancelled(p0: DatabaseError) {

                    }

                    override fun onDataChange(dataSnapshot: DataSnapshot) {
                        dataSnapshot.value?.also {

                        } ?: showNicknameDialog(auth)
                    }
                })
        } ?: showSingUp()
    }

    private fun showSingUp() {
        startActivityForResult(AuthUI.getInstance().createSignInIntentBuilder()
            .setAvailableProviders(mutableListOf(AuthUI.IdpConfig.EmailBuilder().build()))
            .setIsSmartLockEnabled(false).build(), RC_SIGN_IN)
    }

    private fun showNicknameDialog(uid: String, nick: String) {
        val editText = EditText(this)
        editText.setText(nick)
        AlertDialog.Builder(this).setTitle("Nickname")
            .setMessage("Enter your nickname")
            .setView(editText)
            .setPositiveButton("OK") { _, _ ->
                FirebaseDatabase.getInstance().getReference("users")
                    .child(uid)
                    .child("nickname")
                    .setValue(editText.text.toString())
            }.show()
    }

    private fun showNicknameDialog(auth: FirebaseAuth) {
        val displayName = auth.currentUser?.displayName
        val uid = auth.uid
        showNicknameDialog(uid!!, displayName!!)
    }

    override fun onClick(view: View) {
       val selectedId = when(view.id) {
            R.id.avatar_0 -> 0
            R.id.avatar_1 -> 1
            R.id.avatar_2 -> 2
            R.id.avatar_3 -> 3
            R.id.avatar_4 -> 4
            R.id.avatar_5 -> 5
            else -> 0
        }
        FirebaseDatabase.getInstance().getReference("users")
            .child(FirebaseAuth.getInstance().uid!!)
            .child("avatarId")
            .setValue(selectedId)
        group_avatars.visibility = View.GONE
        horizon_scroll.visibility = View.GONE
    }
}
