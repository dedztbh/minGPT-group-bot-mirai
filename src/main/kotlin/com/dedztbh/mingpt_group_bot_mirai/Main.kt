package com.dedztbh.mingpt_group_bot_mirai

import io.ktor.client.*
import io.ktor.client.engine.cio.*
import io.ktor.client.features.json.*
import io.ktor.client.request.*
import io.ktor.http.*
import io.ktor.util.*
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import net.mamoe.mirai.Bot
import net.mamoe.mirai.alsoLogin
import net.mamoe.mirai.event.subscribeAlways
import net.mamoe.mirai.join
import net.mamoe.mirai.message.FriendMessageEvent
import net.mamoe.mirai.message.GroupMessageEvent
import net.mamoe.mirai.message.MessageEvent
import net.mamoe.mirai.message.data.content
import net.mamoe.mirai.message.data.isPlain
import kotlin.properties.Delegates

/**
 * Created by DEDZTBH on 2020/08/21.
 * Project minGPT-group-bot-mirai
 */

const val CONTEXT_SIZE = 3
const val SEND_THRESHOLD = 0.5

var contextQueue = ArrayDeque<String>()
var mutex = Mutex()

lateinit var bot: Bot
lateinit var httpClient: HttpClient
var predictServPort by Delegates.notNull<Int>()

@KtorExperimentalAPI
suspend fun main(args: Array<String>) {
    if (args.size < 3) {
        print("usage: java -jar xxx.jar <qqid> <password> <server_port>")
        return
    }

    predictServPort = args[2].toInt()

    // init bot
    bot = Bot(
        args[0].toLong(), // qq ID
        args[1]           // pass
    ) {
        fileBasedDeviceInfo("device.json")
    }.alsoLogin()

    // init httpClient to communicate with predict server
    httpClient = HttpClient(CIO) {
        install(JsonFeature) {
            serializer = GsonSerializer()
        }
    }

    // add hook for graceful shutdown
    Runtime.getRuntime().addShutdownHook(Thread {
        httpClient.close()
        runBlocking {
            bot.close()
        }
    })

    bot.subscribeAlways<FriendMessageEvent> { event ->
        eventProcessor(event, event.friend::sendMessage)
    }

    bot.subscribeAlways<GroupMessageEvent> { event ->
        eventProcessor(event, event.group::sendMessage)
    }

    bot.join()
}

// json objects for request & response
data class PredictRequest(
    val msgs: List<String>,
    val input_skip_unk: Boolean = true,
    val first_sentence: Boolean = true
)

data class PredictResponse(
    val predict: String,
    val probs: List<Float>,
)

/**
 * Add plain event messages to queue.
 * If queue is longer than @see CONTEXT_SIZE, will send a request to server for prediction and remove first message in queue.
 * Then if its probability is no less than @see SEND_THRESHOLD, call @param callback with message the bot want to send.
 */
suspend fun eventProcessor(event: MessageEvent, callback: suspend (String) -> Unit) = coroutineScope {
    mutex.withLock {
        event.message.filter { it.isPlain() }.forEach {
            contextQueue.add(it.content)
        }
        if (contextQueue.size >= CONTEXT_SIZE) {
            val sendList = mutableListOf<String>()
            sendList.add(contextQueue.removeFirst())
            for (i in 0 until (CONTEXT_SIZE - 1)) {
                sendList.add(contextQueue[i])
            }
            println("sendList: $sendList")
            launch {
                val response = httpClient.post<PredictResponse> {
                    url("http://127.0.0.1:$predictServPort/")
                    contentType(ContentType.Application.Json)
                    body = PredictRequest(
                        msgs = sendList
                    )
                }
                if (response.probs[0] >= SEND_THRESHOLD) {
                    response.predict.split("\n\n")[CONTEXT_SIZE].let {
                        println("response: $it (prob=${response.probs[0]})")
                        // do not say sentence with <unk>
                        if (!it.contains("<unk>")) {
                            callback(it)
                        }
                    }
                }
            }
        }
    }
}