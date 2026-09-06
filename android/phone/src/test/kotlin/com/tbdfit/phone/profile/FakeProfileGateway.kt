package com.tbdfit.phone.profile

class FakeProfileGateway(
    private var profile: Profile? = null,
    private val loadFailure: Throwable? = null,
    private val desiredUsernameHint: String? = null,
) : ProfileGateway {
    var createAttempts = mutableListOf<String>()
        private set

    override suspend fun loadOwnProfile(): Result<Profile?> =
        if (loadFailure != null) Result.failure(loadFailure) else Result.success(profile)

    override suspend fun loadDesiredUsernameHint(): String? = desiredUsernameHint

    override suspend fun createOwnProfile(username: String): Result<Profile> {
        createAttempts.add(username)
        return Result.success(Profile(userId = "uid-1", username = username))
    }
}
