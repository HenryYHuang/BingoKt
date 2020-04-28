package com.wading.bingokt

import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.util.Log
import android.view.View
import android.view.ViewGroup
import androidx.appcompat.app.AlertDialog
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.firebase.ui.common.ChangeEventType
import com.firebase.ui.database.FirebaseRecyclerAdapter
import com.firebase.ui.database.FirebaseRecyclerOptions
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ValueEventListener
import kotlinx.android.synthetic.main.activity_bingo.*
import kotlinx.android.synthetic.main.signle_button.view.*

class BingoActivity : AppCompatActivity(), View.OnClickListener {
    companion object {
        val TAG = BingoActivity::class.java.simpleName
        const val STATUS_INIT = 0
        const val STATUS_CREATED = 1
        const val STATUS_JOINED = 2
        const val STATUS_CREATOR_TURN = 3
        const val STATUS_JOINER_TURN = 4
        const val STATUS_CREATOR_BINGO = 5
        const val STATUS_JOINER_BINGO = 6
        const val STATUS_JOINER_EXIT = 7
    }

    private lateinit var adapter: FirebaseRecyclerAdapter<Boolean, ButtonViewHolder>
    private lateinit var buttons: MutableList<NumberButton>
    private var isCreate  = false
    private lateinit var roomId: String
    private var isSelected = false
    private var haveBingo = false
    private var creatorExit = false
    private var myTurn = false
    set(value) {
        field = value
        game_info.text = if (value) "請選號" else "等待對手選號中"
    }
    private var statusValueListener = object : ValueEventListener {
        override fun onCancelled(databaseError: DatabaseError) {

        }

        override fun onDataChange(dataSnapshot: DataSnapshot) {
            dataSnapshot.value?.let {
                when((it as Long).toInt()) {
                    STATUS_CREATED -> {
                        myTurn = false
                        game_info.text = "等待對手加入中"
                    }
                    STATUS_JOINED -> {
                        game_info.text = "對手已加入"
                        FirebaseDatabase.getInstance().getReference("rooms")
                            .child(roomId)
                            .child("status")
                            .setValue(STATUS_CREATOR_TURN)
                    }
                    STATUS_CREATOR_TURN -> {
                        myTurn = isCreate
                    }
                    STATUS_JOINER_TURN -> {
                        myTurn = !isCreate
                    }
                    STATUS_CREATOR_BINGO -> {
                        haveBingo = true
                        AlertDialog.Builder(this@BingoActivity)
                            .setTitle("賓果!")
                            .setMessage(if (isCreate) "恭喜！你贏了！" else "喔NO! 對方賓果了!")
                            .setPositiveButton("OK") { _, _ ->
                                endGame()
                            }.show()
                    }
                    STATUS_JOINER_BINGO -> {
                        haveBingo = true
                        AlertDialog.Builder(this@BingoActivity)
                            .setTitle("賓果!")
                            .setMessage(if (!isCreate) "恭喜！你贏了！" else "喔NO! 對方賓果了!")
                            .setPositiveButton("OK") { _, _ ->
                                endGame()
                            }.show()
                    }
                    STATUS_JOINER_EXIT -> {
                        AlertDialog.Builder(this@BingoActivity)
                            .setTitle("對手已經離開")
                            .setMessage("遊戲室將關閉")
                            .setPositiveButton("OK") { _, _ ->
                                endGame()
                            }.show()
                    }
                    else -> game_info.text = "等待對手加入中"
                }
            } ?: hostExitRoom()
        }
    }

    private fun hostExitRoom() {
        creatorExit = true
        if (!haveBingo) {
            AlertDialog.Builder(this)
                .setTitle("房主已離開")
                .setMessage("房間將關閉，將返回上一頁")
                .setPositiveButton("OK"
                ) { _, _ ->
                    isSelected = true
                    finish()
                }.show()
        }
    }

    private fun endGame() {
        creatorExit = true
        FirebaseDatabase.getInstance().getReference("rooms")
            .child(roomId)
            .child("status")
            .removeEventListener(statusValueListener)
        if (isCreate) {
            FirebaseDatabase.getInstance().getReference("rooms")
                .child(roomId)
                .removeValue()
        }
        finish()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_bingo)
        roomId = intent.getStringExtra("ROOM_ID") ?: ""
        isCreate = intent.getBooleanExtra("IS_CREATE", false)
        Log.d(TAG, "BingoActivity: $roomId $isCreate")

        if (isCreate) {
            for (i in 1..25) {
                FirebaseDatabase.getInstance().getReference("rooms")
                    .child(roomId)
                    .child("numbers")
                    .child(i.toString())
                    .setValue(false)
            }
            FirebaseDatabase.getInstance().getReference("rooms")
                .child(roomId)
                .child("status")
                .setValue(STATUS_CREATED)
        } else {
//            isSelected = true
            FirebaseDatabase.getInstance().getReference("rooms")
                .child(roomId)
                .child("status")
                .setValue(STATUS_JOINED)
        }

        val numberMap = mutableMapOf<Int, Int>()
        buttons = mutableListOf()
        for (i in 0..24) {
            val btn = NumberButton(this)
            btn.number = i + 1
            buttons.add(btn)
        }

        buttons.shuffle()
        for (i in 0..24) {
            numberMap[buttons[i].number] = i
        }
        recycler.setHasFixedSize(true)
        recycler.layoutManager = GridLayoutManager(this, 5)

        val query = FirebaseDatabase.getInstance().getReference("rooms")
            .child(roomId)
            .child("numbers")
            .orderByKey()
        val options = FirebaseRecyclerOptions.Builder<Boolean>()
            .setQuery(query, Boolean::class.java)
            .build()
        adapter = object : FirebaseRecyclerAdapter<Boolean, ButtonViewHolder>(options) {
            override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ButtonViewHolder {
                val view = layoutInflater.inflate(R.layout.signle_button, parent, false)
                return ButtonViewHolder(view)
            }

            override fun onBindViewHolder(holder: ButtonViewHolder, position: Int, model: Boolean) {
                holder.btn.setOnClickListener(this@BingoActivity)
                holder.btn.text = buttons[position].number.toString()
                holder.btn.number = buttons[position].number
                holder.btn.isEnabled = !buttons[position].picked
            }

            override fun onChildChanged(
                type: ChangeEventType, snapshot: DataSnapshot, newIndex: Int, oldIndex: Int) {
                super.onChildChanged(type, snapshot, newIndex, oldIndex)
                if (type == ChangeEventType.CHANGED) {
                    isSelected = true
                    val number = snapshot.key?.toInt()
                    val pos = numberMap[number]
                    val picked = snapshot.value as Boolean
                    buttons[pos!!].picked = picked
                    val btnViewHolder = recycler.findViewHolderForAdapterPosition(pos) as ButtonViewHolder
                    btnViewHolder.btn.isEnabled = !picked

                    val nums = IntArray(25)
                    for (i in 0..24) {
                        nums[i] = if (buttons[i].picked) 1 else 0
                    }

                    var bingo = 0
                    for (i in 0..4) {
                        var sum = 0
                        for (j in 0..4) {
                            sum += nums[i*5+j]
                        }
                        bingo += if (sum == 5) 1 else 0
                        sum = 0
                        for (j in 0..4) {
                            sum += nums[j*5+i]
                        }
                        bingo += if (sum == 5) 1 else 0
                    }
                    var sum1 = 0
                    for (i in 0..4) {

                        sum1 += nums[i*6]
                        bingo += if (sum1 == 5) 1 else 0
                    }
                    var sum2 = 0
                    for (i in 5 downTo 1) {

                        sum2 += nums[i*4]
                        bingo += if (sum2 == 5) 1 else 0
                    }
                    if (bingo > 0 ) {
                        FirebaseDatabase.getInstance().getReference("rooms")
                            .child(roomId)
                            .child("status")
                            .setValue(if (isCreate) STATUS_CREATOR_BINGO else STATUS_JOINER_BINGO)
                    }
                }
            }
        }

        recycler.adapter = adapter
    }

    override fun onStart() {
        super.onStart()
        adapter.startListening()
        FirebaseDatabase.getInstance().getReference("rooms")
            .child(roomId)
            .child("status")
            .addValueEventListener(statusValueListener)
    }

    override fun onStop() {
        super.onStop()
        if (isCreate) {
            FirebaseDatabase.getInstance().getReference("rooms")
                .child(roomId)
                .removeValue()
        }

        adapter.stopListening()
        FirebaseDatabase.getInstance().getReference("rooms")
            .child(roomId)
            .child("status")
            .removeEventListener(statusValueListener)
    }

    override fun onDestroy() {
        super.onDestroy()

        if (!isCreate && isSelected && !creatorExit) {
            Log.d(TAG, "BingoActivity: 123456789")
            FirebaseDatabase.getInstance().getReference("rooms")
                .child(roomId)
                .child("status")
                .setValue(STATUS_JOINER_EXIT)
        }

        if (!isCreate && !isSelected && !creatorExit) {
            FirebaseDatabase.getInstance().getReference("rooms")
                .child(roomId)
                .child("status")
                .setValue(STATUS_CREATED)
        }
    }

    class ButtonViewHolder(view: View): RecyclerView.ViewHolder(view) {
        lateinit var btn: NumberButton
        init {
            btn = view.button
        }
    }

    override fun onClick(view: View) {
        if (myTurn) {
            val number = (view as NumberButton).number
            FirebaseDatabase.getInstance().getReference("rooms")
                .child(roomId)
                .child("numbers")
                .child(number.toString())
                .setValue(true)
            FirebaseDatabase.getInstance().getReference("rooms")
                .child(roomId)
                .child("status")
                .setValue(if (isCreate) STATUS_JOINER_TURN else STATUS_CREATOR_TURN)
        }
    }

}
