package com.safex.app.data

object FakeAlertRepository {
    fun getAlerts(): List<Alert> {
        return listOf(
            Alert(
                id = "1",
                title = "Suspicious Investment Offer",
                description = "High returns guaranteed in short time.",
                riskLevel = RiskLevel.HIGH,
                timestamp = System.currentTimeMillis() - 3600000,
                source = "Notification",
                reasons = listOf(
                    "Mentions 'guaranteed returns'",
                    "Urgency detected ('act now')",
                    "Unknown sender"
                ),
                actions = listOf(
                    "Do not click links",
                    "Block the sender",
                    "Report to authorities"
                ),
                notToDo = listOf(
                    "Do not transfer money",
                    "Do not share OTP"
                )
            ),
            Alert(
                id = "2",
                title = "Fake Delivery Attempt",
                description = "Your parcel delivery failed. Update address via link.",
                riskLevel = RiskLevel.MEDIUM,
                timestamp = System.currentTimeMillis() - 86400000,
                source = "Notification",
                reasons = listOf(
                    "Suspicious link URL",
                    "Poor grammar"
                ),
                actions = listOf(
                    "Check official app",
                    "Ignore if no parcel expected"
                ),
                notToDo = listOf(
                    "Do not click the link"
                )
            )
        )
    }

    fun getAlert(id: String): Alert? {
        return getAlerts().find { it.id == id }
    }
}
