package com.gradlevv.ocr


class DebitCard(private val number: String, private val expiryMonth: Int, private val expiryYear: Int) {
    fun last4(): String {
        return number.substring(number.length - 4)
    }

    fun expiryForDisplay(): String? {
        if (isExpiryValid) {
            return null
        }
        var month = expiryMonth.toString()
        if (month.length == 1) {
            month = "0$month"
        }
        var year = expiryYear.toString()
        if (year.length == 4) {
            year = year.substring(2)
        }
        return "$month/$year"
    }

    val isExpiryValid: Boolean
        get() = expiryMonth <= 0 || expiryMonth > 12 || expiryYear <= 0

}