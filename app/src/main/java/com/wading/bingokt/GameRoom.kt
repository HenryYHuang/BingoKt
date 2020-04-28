package com.wading.bingokt

class GameRoom(var id: String, var title: String,
               var status: Int, var init: Member?, var join: Member?) {
    constructor(): this("", "Welcome", 0, null, null)
    constructor(title: String, init: Member?) : this("", title, 0, init, null)
}