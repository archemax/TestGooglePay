package com.example.newgooglepay.util



import android.app.Activity
import com.example.newgooglepay.Constants

import com.google.android.gms.wallet.PaymentsClient
import com.google.android.gms.wallet.Wallet
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject
import java.math.BigDecimal
import java.math.RoundingMode


object PaymentsUtil {
    val CENTS = BigDecimal(100)


    private val baseRequest = JSONObject().apply {
        put("apiVersion", 2)
        put("apiVersionMinor", 0)
    }


    private fun gatewayTokenizationSpecification(): JSONObject {
        return JSONObject().apply {
            put("type", "PAYMENT_GATEWAY")
            put("parameters", JSONObject(Constants.PAYMENT_GATEWAY_TOKENIZATION_PARAMETERS))
        }
    }


    private fun directTokenizationSpecification(): JSONObject {
        if (Constants.DIRECT_TOKENIZATION_PUBLIC_KEY == "REPLACE_ME" ||
            (Constants.DIRECT_TOKENIZATION_PARAMETERS.isEmpty() ||
                    Constants.DIRECT_TOKENIZATION_PUBLIC_KEY.isEmpty())) {

            throw RuntimeException(
                "Please edit the Constants.java file to add protocol version & public key.")
        }

        return JSONObject().apply {
            put("type", "DIRECT")
            put("parameters", JSONObject(Constants.DIRECT_TOKENIZATION_PARAMETERS))
        }
    }


    private val allowedCardNetworks = JSONArray(Constants.SUPPORTED_NETWORKS)


    private val allowedCardAuthMethods = JSONArray(Constants.SUPPORTED_METHODS)


    // Optionally, you can add billing address/phone number associated with a CARD payment method.
    private fun baseCardPaymentMethod(): JSONObject {
        return JSONObject().apply {

            val parameters = JSONObject().apply {
                put("allowedAuthMethods", allowedCardAuthMethods)
                put("allowedCardNetworks", allowedCardNetworks)
                put("billingAddressRequired", true)
                put("billingAddressParameters", JSONObject().apply {
                    put("format", "FULL")
                })
            }

            put("type", "CARD")
            put("parameters", parameters)
        }
    }


    private fun cardPaymentMethod(): JSONObject {
        val cardPaymentMethod = baseCardPaymentMethod()
        cardPaymentMethod.put("tokenizationSpecification", gatewayTokenizationSpecification())

        return cardPaymentMethod
    }

    fun isReadyToPayRequest(): JSONObject? {
        return try {
            baseRequest.apply {
                put("allowedPaymentMethods", JSONArray().put(baseCardPaymentMethod()))
            }

        } catch (e: JSONException) {
            null
        }
    }


    private val merchantInfo: JSONObject =
        JSONObject().put("merchantName", "Example Merchant")


    fun createPaymentsClient(activity: Activity): PaymentsClient {
        val walletOptions = Wallet.WalletOptions.Builder()
            .setEnvironment(Constants.PAYMENTS_ENVIRONMENT)
            .build()

        return Wallet.getPaymentsClient(activity, walletOptions)
    }


    @Throws(JSONException::class)
    private fun getTransactionInfo(price: String): JSONObject {
        return JSONObject().apply {
            put("totalPrice", price)
            put("totalPriceStatus", "FINAL")
            put("countryCode", Constants.COUNTRY_CODE)
            put("currencyCode", Constants.CURRENCY_CODE)
        }
    }


    fun getPaymentDataRequest(priceCemts: Long): JSONObject? {
        return try {
            baseRequest.apply {
                put("allowedPaymentMethods", JSONArray().put(cardPaymentMethod()))
                put("transactionInfo", getTransactionInfo(priceCemts.centsToString()))
                put("merchantInfo", merchantInfo)

                // An optional shipping address requirement is a top-level property of the
                // PaymentDataRequest JSON object.
                val shippingAddressParameters = JSONObject().apply {
                    put("phoneNumberRequired", false)
                    put("allowedCountryCodes", JSONArray(listOf("US", "GB")))
                }
                put("shippingAddressParameters", shippingAddressParameters)
                put("shippingAddressRequired", true)
            }
        } catch (e: JSONException) {
            null
        }
    }
}


fun Long.centsToString() = BigDecimal(this)
    .divide(PaymentsUtil.CENTS)
    .setScale(2, RoundingMode.HALF_EVEN)
    .toString()