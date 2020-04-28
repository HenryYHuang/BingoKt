package com.wading.bingokt

data class Member(var uid: String,
                  var displayName: String,
                  var nickname: String?,
                  var avatarId: Int) {
    constructor() : this("", "", null, 0)
}