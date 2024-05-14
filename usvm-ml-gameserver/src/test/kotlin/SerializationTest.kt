import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.jupiter.api.Test
import org.usvm.gameserver.*

class SerializationTest {
    @Test
    fun moveRewardTest() {
        val reward = Reward(1u, 2u, 3u)
        val moveReward = MoveReward(reward)

        val serialized = Json.encodeToString(moveReward)
        val deserialized = Json.decodeFromString<MoveReward>(serialized)

        assert(deserialized == moveReward)
    }

    @Test
    fun gameStateTest() {
        val gameState = GameState(listOf(), listOf(), listOf())

        val serialized = Json.encodeToString(gameState)
        val deserialized = Json.decodeFromString<GameState>(serialized)

        assert(deserialized == gameState)
    }

    @Test
    fun moveRewardRawOutputMessageTest() {
        val reward = Reward(1u, 2u, 3u)
        val moveReward = MoveReward(reward)
        val rawOutputMessage = RawOutputMessage(OutputMessageType.MoveReward, moveReward)

        val serialized =
            Json.encodeToString(RawOutputMessage.serializer(), rawOutputMessage)
        val deserialized = Json.decodeFromString<RawOutputMessage>(serialized)

        assert(deserialized == rawOutputMessage)
    }

    @Test
    fun gameStateRawOutputMessageTest() {
        val gameState = GameState(
            listOf(
                GameMapVertex(
                    1u, true, 3u,
                    coveredByTest = true,
                    visitedByState = true,
                    touchedByState = true,
                    containsCall = true,
                    containsThrow = true,
                    states = listOf(1u, 3u)
                )
            ),
            listOf(),
            listOf()
        )
        val rawOutputMessage = RawOutputMessage(OutputMessageType.ReadyForNextStep, ReadyForNextStep(gameState))

        val serialized = Json.encodeToString(rawOutputMessage)
        val deserialized = Json.decodeFromString<RawOutputMessage>(serialized)

        assert(deserialized == rawOutputMessage)
    }

    @Test
    fun stepDeserializeInputTest() {
        val inputMessage =
            """{"MessageType":"step","MessageBody":"{\"StateId\":1,\"PredictedStateUsefulness\":42.0}"}"""

        val deserialized = Json.decodeFromString<RawInputMessage>(inputMessage)

        assert(deserialized == RawInputMessage(InputMessageType.Step, Step(GameStep(1u))))
    }

    @Test
    fun stateIdRawOutputTest() {
        val stateId = 1u
        val inputMessage = RawInputMessage(InputMessageType.Step, Step(GameStep(stateId)))

        val serialized = Json.encodeToString(inputMessage)
        val deserialized = Json.decodeFromString<RawInputMessage>(serialized)

        assert(deserialized == inputMessage)
    }

    @Test
    fun stopRawInputTest() {
        val inputMessage = RawInputMessage(InputMessageType.Stop, ServerStop)

        val serialized = Json.encodeToString(inputMessage)
        val deserialized = Json.decodeFromString<RawInputMessage>(serialized)
        assert(deserialized == inputMessage)
    }
}
