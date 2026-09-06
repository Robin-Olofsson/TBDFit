package com.tbdfit.phone.wearreplication

class FakePhoneAckSender(
    private val onSendAck: suspend (recordId: String) -> Unit = {},
    private val shouldSucceed: Boolean = true,
) : PhoneAckSender {
    val ackAttempts = mutableListOf<String>()

    override suspend fun sendAck(recordId: String): Result<Unit> {
        ackAttempts.add(recordId)
        onSendAck(recordId)
        return if (shouldSucceed) Result.success(Unit) else Result.failure(IllegalStateException("simulated ack failure"))
    }
}
