package com.team.runnershi

import android.content.Context
import android.content.Intent
import android.os.*
import android.util.Log
import androidx.core.app.JobIntentService
import com.team.runnershi.extension.logDebug
import io.socket.client.IO
import io.socket.client.Socket
import io.socket.emitter.Emitter
import java.net.URISyntaxException

class SocketService : JobIntentService() {
    private val TAG = SocketService::class.simpleName
    lateinit var socketThread: SocketThread
    private lateinit var mSocket: Socket
    private var isSocketExist = false
    private val mHost = "http://13.125.20.117:3000/matching"
    private var roomName: String = ""
    private lateinit var resultReceiver: ResultReceiver

    override fun onHandleWork(intent: Intent) {
        if (!isSocketExist) {
            try {
                socketConnect()
            } catch (e: URISyntaxException) {
                Log.d(
                    TAG,
                    "Socket Connect Reason: ${e.reason} (index: ${e.index}) (message:${e.message}"
                )
            }
            isSocketExist = true
        }
        "Appear on onHandleWork".logDebug(this@SocketService)
        when (intent.getStringExtra("serviceFlag")) {
            "joinRoom" -> {
                resultReceiver = intent.getParcelableExtra("receiver")!!
                val token = intent.getStringExtra("token")
                val time = intent.getIntExtra("time", -1) * 60
                val wantGender = intent.getIntExtra("wantGender", -1)
                val leftTime = intent.getIntExtra("leftTime", -1)
                "JoinRoom (token: $token) (time: $time) (wantGender: $wantGender) (leftTime: $leftTime)".logDebug(
                    this@SocketService
                )
                "Send JoinRoom Result Receiver :$resultReceiver".logDebug(this@SocketService)
                mSocket.emit("joinRoom", token, time, wantGender, leftTime)
            }
            "stopMatching" -> {
                mSocket.emit("stopMatching", roomName)
            }
            "readyToRun" -> {
                resultReceiver = intent.getParcelableExtra("receiver")!!
                mSocket.emit("readyToRun", roomName)
                "Send Ready to Run (Result Reciever $resultReceiver) (roomName: $roomName)".logDebug(SocketService::class)
            }
            "kmPasssed" -> {
                val km = intent.getIntExtra("km", -1)
                mSocket.emit("kmPassed", roomName, km)
            }
            "stopRunning" -> {
                val distance = intent.getIntExtra("distance", -1) // meter
                val time = intent.getIntExtra("time", -1) // 초
                val coordinates = intent.getSerializableExtra("coordinates") // location
                mSocket.emit("stopRunning", roomName, distance, time, coordinates)
            }
            "endRunning" -> {
                val distance = intent.getIntExtra("distance", -1) // meter
                val time = intent.getIntExtra("time", -1) // 초
                val coordinates = intent.getSerializableExtra("coordinates") // location
                mSocket.emit("endRunning", roomName, distance, time, coordinates)
            }
            "runComplete" -> {
                mSocket.emit("runComplete", roomName)
            }
        }
    }

    private fun socketConnect() {
        mSocket = IO.socket(mHost)
        mSocket.on(Socket.EVENT_CONNECT, onConnect)
        mSocket.on(Socket.EVENT_DISCONNECT, onDisconnect)
        mSocket.on(Socket.EVENT_CONNECT_TIMEOUT, onConnctTimeOut)
        mSocket.on(Socket.EVENT_CONNECT_ERROR, onConnectError)
        mSocket.on("start", onStart)
        mSocket.on("joinRoom", onJoinRoom)
        mSocket.on("roomCreated", onRoomCreated)
        mSocket.on("timeLeft", onTimeLeft)
        mSocket.on("timeOver", onTimeOver)
        mSocket.on("stopCount", onStopCount)
        mSocket.on("leaveRoom", onLeaveRoom)
        mSocket.on("roomFull", onRoomFull)
        mSocket.on("opponentInfo", onOpponentInfo)
        mSocket.on("letsRun", onLetsRun)
        mSocket.on("opponentNotReady", onOpponentNotReady)
        mSocket.on("kmPassed", onKmPassed)
        mSocket.on("opponentStopped", onOpponentStopped)
        mSocket.on("stopRunning", onStopRunning)
        mSocket.on("endRunning", onEndRunning)
        mSocket.on("compareResult", onCompareResult)
        mSocket.on("error", onError)
        mSocket.connect()
    }

    private val onStart: Emitter.Listener = Emitter.Listener {
        Log.d(TAG, "Socket onStart")
    }

    private val onConnect: Emitter.Listener = Emitter.Listener {
        Log.d(TAG, "Socket onConnect")
        Log.d(TAG, "Connect Time = ${SystemClock.elapsedRealtime()}")
    }
    private val onDisconnect: Emitter.Listener = Emitter.Listener {
        Log.d(TAG, "Socket onDisconnect")
        Log.d(TAG, "Connect Time = ${SystemClock.elapsedRealtime()}")
    }
    private val onConnctTimeOut: Emitter.Listener = Emitter.Listener {
        Log.d(TAG, "Socket onConnectTimeOut")
    }

    private val onConnectError: Emitter.Listener = Emitter.Listener {
        "Socket onConnectError".logDebug(SocketService)
    }

    private val onJoinRoom: Emitter.Listener = Emitter.Listener {
        val token = it[0].toString()
        Log.d(TAG, "Socket onJoinRoom, (token: $token)")
    }

    private val onRoomCreated: Emitter.Listener = Emitter.Listener {
        Log.d(TAG, "Socket onRoomCreated")
        roomName = it[0].toString()
        mSocket.emit("startCount", roomName)

        val bundle = Bundle()
        bundle.putString("roomName", roomName)
        resultReceiver.send(RESULT_ROOM_NAME, bundle)
    }

    private val onTimeLeft: Emitter.Listener = Emitter.Listener {
        Log.d(TAG, "Socket onTimeLeft")
        val leftTime = it[0] as Int
        val bundle = Bundle()
        bundle.putInt("leftTime", leftTime)
        resultReceiver.send(RESULT_LEFT_TIME, bundle)
    }

    private val onTimeOver: Emitter.Listener = Emitter.Listener {
        Log.d(TAG, "Socket onTimeOver")
        socketDisconnect()
    }

    private val onStopCount: Emitter.Listener = Emitter.Listener {
        Log.d(TAG, "Socket onStopCount")
        mSocket.emit("leaveRoom", roomName)
    }
    private val onLeaveRoom: Emitter.Listener = Emitter.Listener {
        Log.d(TAG, "Socket onLeaveRoom")
        socketDisconnect()
    }


    private val onRoomFull: Emitter.Listener = Emitter.Listener {
        roomName = it[0].toString()
        Log.d(TAG, "Socket onRoomFull (roomName: $roomName)")
        mSocket.emit("opponentInfo", roomName)
    }

    private val onOpponentInfo: Emitter.Listener = Emitter.Listener {
        Log.d(TAG, "Socket onOpponenetInfo")
        roomName = it[0].toString()
        val name = it[1].toString()
        val level = it[2] as Int
        val win = it[3] as Int
        val lose = it[4] as Int
        val image = it[5] as Int
        "(roomName: $roomName) (name: $name) (level: $level)  (win: $win) (lose: $lose) (image: $image)".logDebug(this@SocketService)

        val bundle = Bundle()
        with(bundle){
            this.putString("name", name)
            this.putInt("level", level)
            this.putInt("win", win)
            this.putInt("lose", lose)
            this.putInt("image", image)
        }
        resultReceiver.send(RESULT_OPPONENT_INFO, bundle)
    }

    private val onLetsRun: Emitter.Listener = Emitter.Listener {
        "Socket onLetsRun".logDebug(this@SocketService)
        resultReceiver.send(RESULT_LETS_RUN, Bundle())
    }

    private val onOpponentNotReady: Emitter.Listener = Emitter.Listener {
        "Socket onOpponentNotReady".logDebug(SocketService)
    }

    private val onKmPassed: Emitter.Listener = Emitter.Listener {
        Log.d(TAG, "Socket onKmPassed")
        // todo 음성 알림 보내기 = TTS
    }

    private val onStopRunning: Emitter.Listener = Emitter.Listener {
        Log.d(TAG, "Socket onStopRunning")
        mSocket.emit("leaveRoom", roomName)
    }

    private val onOpponentStopped: Emitter.Listener = Emitter.Listener {
        Log.d(TAG, "Socket onOpponentStopped")
    }

    private val onEndRunning: Emitter.Listener = Emitter.Listener {
        Log.d(TAG, "Socket onEndRunning")
        // todo 뷰를 finish 로 넘김
    }

    private val onCompareResult: Emitter.Listener = Emitter.Listener {
        Log.d(TAG, "Socket onCompareResult")
        socketDisconnect()
        // todo 뷰를 finish 로 넘김
    }


    private val onError: Emitter.Listener = Emitter.Listener {
        "Socket onError".logDebug(SocketService)
    }


    override fun onStopCurrentWork(): Boolean {
        "onStopCurrentWork".logDebug(this@SocketService)
        return super.onStopCurrentWork()
    }

    override fun onDestroy() {
        super.onDestroy()
        "SocketService is destroyed".logDebug(SocketService::class)
    }

    private fun socketDisconnect() {
        mSocket.off(Socket.EVENT_CONNECT, onConnect)
        mSocket.off(Socket.EVENT_DISCONNECT, onDisconnect)
        mSocket.off(Socket.EVENT_CONNECT_TIMEOUT, onConnctTimeOut)
        mSocket.off("start", onStart)
        mSocket.off("joinRoom", onJoinRoom)
        mSocket.off("roomCreated", onRoomCreated)
        mSocket.off("timeLeft", onTimeLeft)
        mSocket.off("timeOver", onTimeOver)
        mSocket.off("stopMatching", onStopCount)
        mSocket.off("roomFull", onRoomFull)
        mSocket.off("kmPassed", onKmPassed)
        mSocket.off("opponenetStopped", onOpponentStopped)
        mSocket.off("stopRunning", onStopRunning)
        mSocket.off("endRunning", onEndRunning)
        mSocket.disconnect()
    }

    fun getSocket(): Socket {
        mSocket?.let {
            return it
        }
    }

    companion object {
        const val JOB_ID = 1001
        const val RESULT_LEFT_TIME = 111
        const val RESULT_OPPONENT_INFO = 222
        const val RESULT_LETS_RUN = 333
        const val RESULT_ROOM_NAME = 444
        fun enqueueWork(context: Context, work: Intent) {
            enqueueWork(context, SocketService::class.java, JOB_ID, work)
        }
    }

    inner class SocketThread : Thread() {
        override fun run() {
            super.run()
            try {
//                socketConnect()
            } catch (e: URISyntaxException) {
                Log.d(
                    TAG,
                    "Socket Connect Reason: ${e.reason} (index: ${e.index}) (message:${e.message}"
                )
            }
        }

    }
}
