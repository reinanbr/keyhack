package com.reinanbr.keyhack

import com.reinanbr.keyhack.net.Network

/** Online build: GIF search gets its network access. */
internal object BuildSetup {
    fun install() = Network.install()
}
