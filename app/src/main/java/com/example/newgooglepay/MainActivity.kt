package com.example.newgooglepay

import android.annotation.SuppressLint
import android.content.Intent
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.text.Html
import android.util.Log
import android.view.View
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Button
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import com.example.newgooglepay.util.PaymentsUtil
import com.google.android.gms.common.api.ApiException
import com.google.android.gms.samples.wallet.util.Json
import com.google.android.gms.wallet.AutoResolveHelper
import com.google.android.gms.wallet.IsReadyToPayRequest
import com.google.android.gms.wallet.PaymentData
import com.google.android.gms.wallet.PaymentDataRequest
import com.google.android.gms.wallet.PaymentsClient
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject

class MainActivity : AppCompatActivity() {

    private val SHIPPING_COST_CENTS = 9 * PaymentsUtil.CENTS.toLong()
    private lateinit var paymentsClient: PaymentsClient
    private lateinit var garmentList: JSONArray
    private lateinit var selectedGarment: JSONObject
    private val LOAD_PAYMENT_DATA_REQUEST_CODE = 991
    private lateinit var webView: WebView
    private lateinit var googlePayButton: Button


    private lateinit var detailTitle: TextView
    private lateinit var detailDescription: TextView
    private lateinit var detailPrice: TextView
    private lateinit var detailImage: ImageView

    @SuppressLint("MissingInflatedId")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        webView = findViewById(R.id.webView)
        googlePayButton = findViewById<Button>(R.id.googlePayButton)

        detailTitle = findViewById(R.id.detailTitle)
        detailDescription = findViewById(R.id.detailDescription)
        detailPrice = findViewById(R.id.detailPrice)
        detailImage = findViewById(R.id.detailImage)

        setupWebView()

        val website = "https://fora.ua"
        webView.loadUrl(website)

        // Set up the mock information for our item in the UI.
//        selectedGarment = fetchRandomGarment()
//        displayGarment(selectedGarment)

        //paymentsClient = PaymentsUtil.createPaymentsClient(this)
        //possiblyShowGooglePayButton()

        googlePayButton.setOnClickListener { requestPayment() }
    }


    private fun setupWebView() {
        webView.webViewClient = object : WebViewClient() {
            override fun shouldOverrideUrlLoading(
                view: WebView?,
                request: WebResourceRequest?
            ): Boolean {
                // Intercept URL loading to ensure it stays within the WebView
                return false
            }
        }

        val settings = webView.settings
        settings.javaScriptEnabled = true
        settings.domStorageEnabled = true


    }
    override fun onBackPressed() {
        if (webView.canGoBack()) {
            webView.goBack()
        } else {
            onBackPressedDispatcher.onBackPressed()
        }
    }


    private fun possiblyShowGooglePayButton() {

        val isReadyToPayJson = PaymentsUtil.isReadyToPayRequest() ?: return
        val request = IsReadyToPayRequest.fromJson(isReadyToPayJson.toString()) ?: return

        val task = paymentsClient.isReadyToPay(request)
        task.addOnCompleteListener { completedTask ->
            try {
                completedTask.getResult(ApiException::class.java)?.let(::setGooglePayAvailable)
            } catch (exception: ApiException) {
                // Process error
                Log.w("isReadyToPay failed", exception)
            }
        }
    }


    private fun setGooglePayAvailable(available: Boolean) {
        if (available) {
            //googlePayButton.visibility = View.VISIBLE
        } else {
            Toast.makeText(
                this,
                "Unfortunately, Google Pay is not available on this device",
                Toast.LENGTH_LONG
            ).show();
        }
    }

    private fun requestPayment() {

        // Disables the button to prevent multiple clicks.
        googlePayButton.isClickable = false

        // The price provided to the API should include taxes and shipping.
        // This price is not displayed to the user.
        val garmentPrice = selectedGarment.getDouble("price")
        val priceCents =
            Math.round(garmentPrice * PaymentsUtil.CENTS.toLong()) + SHIPPING_COST_CENTS

        val paymentDataRequestJson = PaymentsUtil.getPaymentDataRequest(priceCents)
        if (paymentDataRequestJson == null) {
            Log.e("RequestPayment", "Can't fetch payment data request")
            return
        }
        val request = PaymentDataRequest.fromJson(paymentDataRequestJson.toString())

        // Since loadPaymentData may show the UI asking the user to select a payment method, we use
        // AutoResolveHelper to wait for the user interacting with it. Once completed,
        // onActivityResult will be called with the result.
        if (request != null) {
            AutoResolveHelper.resolveTask(
                paymentsClient.loadPaymentData(request), this, LOAD_PAYMENT_DATA_REQUEST_CODE
            )
        }
    }


    public override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)

        when (requestCode) {
            // Value passed in AutoResolveHelper
            LOAD_PAYMENT_DATA_REQUEST_CODE -> {
                when (resultCode) {
                    RESULT_OK ->
                        data?.let { intent ->
                            PaymentData.getFromIntent(intent)?.let(::handlePaymentSuccess)
                        }

                    RESULT_CANCELED -> {
                        // The user cancelled the payment attempt
                    }

                    AutoResolveHelper.RESULT_ERROR -> {
                        AutoResolveHelper.getStatusFromIntent(data)?.let {
                            handleError(it.statusCode)
                        }
                    }
                }

                // Re-enables the Google Pay payment button.
                googlePayButton.isClickable = true
            }
        }
    }


    private fun handlePaymentSuccess(paymentData: PaymentData) {
        val paymentInformation = paymentData.toJson() ?: return

        try {
            // Token will be null if PaymentDataRequest was not constructed using fromJson(String).
            val paymentMethodData =
                JSONObject(paymentInformation).getJSONObject("paymentMethodData")
            val billingName = paymentMethodData.getJSONObject("info")
                .getJSONObject("billingAddress").getString("name")
            Log.d("BillingName", billingName)

            //Toast.makeText(this, getString(R.string.payments_show_name, billingName), Toast.LENGTH_LONG).show()

            // Logging token string.
            Log.d(
                "GooglePaymentToken", paymentMethodData
                    .getJSONObject("tokenizationData")
                    .getString("token")
            )

        } catch (e: JSONException) {
            Log.e("handlePaymentSuccess", "Error: " + e.toString())
        }

    }


    private fun handleError(statusCode: Int) {
        Log.w("loadPaymentData failed", String.format("Error code: %d", statusCode))
    }

    private fun fetchRandomGarment(): JSONObject {
        if (!::garmentList.isInitialized) {
            garmentList = Json.readFromResources(this, R.raw.tshirts)
        }

        val randomIndex: Int = Math.round(Math.random() * (garmentList.length() - 1)).toInt()
        return garmentList.getJSONObject(randomIndex)
    }

//    private fun displayGarment(garment: JSONObject) {
//        detailTitle.setText(garment.getString("title"))
//        detailPrice.setText("\$${garment.getString("price")}")
//
//        val escapedHtmlText: String = Html.fromHtml(garment.getString("description")).toString()
//        detailDescription.setText(Html.fromHtml(escapedHtmlText))
//
//        val imageUri = "@drawable/${garment.getString("image")}"
//        val imageResource = resources.getIdentifier(imageUri, null, packageName)
//        detailImage.setImageResource(imageResource)
//    }

}