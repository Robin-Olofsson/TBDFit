package com.tbdfit.phone.profile

// The TBDFit product profile — deliberately minimal. user_id is the durable account identity
// (Supabase auth.users.id, unchanged by anything here); username is the one product-owned field
// this slice needs. Do not add product fields here without a concrete requirement.
data class Profile(
    val userId: String,
    val username: String,
)
