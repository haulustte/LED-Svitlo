package com.bitapps.ledsvitlo

import android.annotation.SuppressLint
import android.os.AsyncTask
import android.os.Bundle
import android.os.StrictMode
import android.os.StrictMode.ThreadPolicy
import android.text.Editable
import android.util.Log
import android.util.Patterns
import android.view.View
import android.widget.Button
import android.widget.CompoundButton
import android.widget.ImageView
import android.widget.SeekBar
import android.widget.SeekBar.OnSeekBarChangeListener
import android.widget.TextView
import android.widget.Toast
import android.widget.ToggleButton
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout

class MainActivity : AppCompatActivity() {
    private val LOG_TAG = MainActivity::class.java.simpleName

    private val PSU_OFF = "PSU OFF"
    private val PSU_ON = "PSU ON"
    private val LED_ALL_ON = "LED ALL ON"
    private val LED_ALL_OFF = "LED ALL OFF"
    val GET_TEMP = "gettemp"
    val GET_ADC_DATA_V = "getadcdata v"

    var led1: ImageView? = null
    var led2: ImageView? = null
    var led3: ImageView? = null
    var led4: ImageView? = null
    var brightnessSeekbar: SeekBar? = null
    var temperatureSeekbar: SeekBar? = null
    var psu: Button? = null
    var ledAll: Button? = null
    var temperatureTextView: TextView? = null
    var voltageTextView: TextView? = null
    var colorTemperatureTextView: TextView? = null
    var ipPortTextInput: TextInputLayout? = null
    var ipPortEditText: TextInputEditText? = null
    var ipPortToggleButton: ToggleButton? = null

    var mActivity: MainActivity? = null

    var led1Toogle = false
    var led2Toogle = false
    var led3Toogle = false
    var led4Toogle = false

    var oldProgress = 100

    var continueReceivingTempAndVoltageData = true

    @SuppressLint("SetTextI18n")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Log.i(LOG_TAG, "onCreate()")
        setContentView(R.layout.activity_main)
        mActivity = this
        val policy = ThreadPolicy.Builder().permitAll().build()
        StrictMode.setThreadPolicy(policy)
        led1 = findViewById(R.id.led1_nw) as ImageView
        led2 = findViewById(R.id.led2_ww) as ImageView
        led3 = findViewById(R.id.led3_ww) as ImageView
        led4 = findViewById(R.id.led4_nw) as ImageView
        brightnessSeekbar = findViewById(R.id.brightness_seekbar) as SeekBar
        temperatureSeekbar = findViewById(R.id.color_temperature_seekbar) as SeekBar
        psu = findViewById(R.id.psu_button) as Button
        ledAll = findViewById(R.id.led_all_button) as Button
        temperatureTextView = findViewById(R.id.temperature_textview) as TextView
        voltageTextView = findViewById(R.id.voltage_textview) as TextView
        colorTemperatureTextView = findViewById(R.id.color_temperature_textview) as TextView
        ipPortTextInput = findViewById(R.id.ipPortTextInput)
        ipPortToggleButton = findViewById(R.id.ipPortToggleButton)
        ipPortEditText = findViewById(R.id.ipPortEditText)
        colorTemperatureTextView!!.text =
            applicationContext.resources.getString(R.string.color_temperature_text)
        led1Toogle = false
        led2Toogle = false
        led3Toogle = false
        led4Toogle = false
        led1!!.contentDescription = "1"
        led2!!.contentDescription = "2"
        led3!!.contentDescription = "3"
        led4!!.contentDescription = "4"
        led1!!.setOnClickListener(ledOnClickListener)
        led2!!.setOnClickListener(ledOnClickListener)
        led3!!.setOnClickListener(ledOnClickListener)
        led4!!.setOnClickListener(ledOnClickListener)
        brightnessSeekbar!!.progress = 0
        brightnessSeekbar!!.keyProgressIncrement = 1
        brightnessSeekbar!!.max = 200
        brightnessSeekbar!!.setOnSeekBarChangeListener(seekBar1ChangeListener)
        temperatureSeekbar!!.progress = 100
        temperatureSeekbar!!.keyProgressIncrement = 1
        temperatureSeekbar!!.max = 200
        temperatureSeekbar!!.setOnSeekBarChangeListener(temperatureChangeListener)
        psu!!.text = PSU_OFF
        psu!!.setOnClickListener(psuOnClickListener)
        ledAll!!.text = LED_ALL_OFF
        ledAll!!.setOnClickListener(ledAllClickListener)
        temperatureTextView!!.setText(Constants.TEXT_TEMPERATURE)
        voltageTextView!!.setText(Constants.TEXT_VOLTAGE)
        enableCheckingUpdates()
        ipPortTextInput!!.setEnabled(false)
        ipPortToggleButton!!.setOnCheckedChangeListener(object :
            CompoundButton.OnCheckedChangeListener {
            var isOnFromError = false
            override fun onCheckedChanged(compoundButton: CompoundButton, b: Boolean) {
                ipPortTextInput!!.setEnabled(b)
                if (!b) {
                    val sharedPreferences = getSharedPreferences("main_sp", MODE_PRIVATE)
                    val editable: Editable = ipPortEditText!!.getText()!!
                    if (editable != null && editable.toString().contains(":") && editable.toString()
                            .split(":".toRegex()).dropLastWhile { it.isEmpty() }
                            .toTypedArray().size > 1 && Patterns.IP_ADDRESS.matcher(
                            editable.toString().split(":".toRegex()).dropLastWhile { it.isEmpty() }
                                .toTypedArray()[0]).matches()
                        && editable.toString().split(":".toRegex()).dropLastWhile { it.isEmpty() }
                            .toTypedArray()[1].matches("\\d+".toRegex())
                    ) {
                        val parts =
                            editable.toString().split(":".toRegex()).dropLastWhile { it.isEmpty() }
                                .toTypedArray()
                        sharedPreferences.edit().putString("ip", parts[0]).apply()
                        sharedPreferences.edit().putInt("port", parts[1].toInt()).apply()
                    } else {
                        Toast.makeText(this@MainActivity, "Wrong syntax!", Toast.LENGTH_SHORT)
                            .show()
                        isOnFromError = true
                        ipPortToggleButton!!.setChecked(true)
                        ipPortTextInput!!.setEnabled(true)
                    }
                }
            }
        })
        val sharedPreferences = getSharedPreferences("main_sp", MODE_PRIVATE)
        val ip = sharedPreferences.getString("ip", "0.0.0.0")
        val port = sharedPreferences.getInt("port", 0)
        ipPortEditText!!.setText("$ip:$port")
    }

    private fun enableCheckingUpdates() {
        object : AsyncTask<Void?, Void?, Void?>() {
            override fun doInBackground(vararg p0: Void?): Void? {
                while (true) {
                    if (!continueReceivingTempAndVoltageData) continue
                    val receivedTemperatureData: ArrayList<String> =
                        UDPClient.clientNeedToReceiveData(this@MainActivity, GET_TEMP)
                    val receivedVoltageData: ArrayList<String> =
                        UDPClient.clientNeedToReceiveData(this@MainActivity, GET_ADC_DATA_V)
                    //Log.i(LOG_TAG, "data received");
                    if (receivedTemperatureData == null) continue
                    if (receivedVoltageData == null) continue
                    val temperatureData: String =
                        Parcer.parseString(receivedTemperatureData, Constants.CODE_TEMPERATURE)
                    val voltageData: String =
                        Parcer.parseString(receivedVoltageData, Constants.CODE_VOLTAGE)
                    mActivity!!.runOnUiThread {
                        temperatureTextView =
                            mActivity!!.findViewById(R.id.temperature_textview) as TextView
                        voltageTextView =
                            mActivity!!.findViewById(R.id.voltage_textview) as TextView
                        temperatureTextView!!.setText(Constants.TEXT_TEMPERATURE + temperatureData)
                        voltageTextView!!.setText(Constants.TEXT_VOLTAGE + voltageData)
                    }
                    try {
                        Thread.sleep(2000)
                    } catch (e: InterruptedException) {
                        e.printStackTrace()
                    }
                }
            }
        }.execute()
    }

    private val ledAllClickListener = View.OnClickListener {
        continueReceivingTempAndVoltageData = false
        if (ledAll!!.text == LED_ALL_OFF) {
            ledAll!!.text = LED_ALL_ON
            UDPClient.client(this, LED_ALL_ON)
        } else {
            UDPClient.client(this, LED_ALL_OFF)
            ledAll!!.text = LED_ALL_OFF
        }
        continueReceivingTempAndVoltageData = true
    }

    private val temperatureChangeListener: OnSeekBarChangeListener =
        object : OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar, progress: Int, fromUser: Boolean) {
                val sb1 = StringBuilder("LED 1 NC ")
                val sb2 = StringBuilder("LED 2 NC ")
                val sb3 = StringBuilder("LED 3 NC ")
                val sb4 = StringBuilder("LED 4 NC ")
                val pwmValueHot = 45000 + 100 * progress
                val pwmValueCold = 65000 - 100 * progress
                Log.i(LOG_TAG, "pwmValueHot $pwmValueHot")
                Log.i(LOG_TAG, "pwmValueCold $pwmValueCold")

//            sb1.append(pwmValueHot);
//            sb2.append(pwmValueCold);
//            sb3.append(pwmValueCold);
//            sb4.append(pwmValueHot);
                if (progress < oldProgress) {
                    sb1.append("-2")
                    sb2.append("+2")
                    sb3.append("+2")
                    sb4.append("-2")
                } else {
                    sb1.append("+2")
                    sb2.append("-2")
                    sb3.append("-2")
                    sb4.append("+2")
                }
                UDPClient.client(this@MainActivity, sb1.toString())
                UDPClient.client(this@MainActivity, sb2.toString())
                UDPClient.client(this@MainActivity, sb3.toString())
                UDPClient.client(this@MainActivity, sb4.toString())
                oldProgress = progress
            }

            override fun onStartTrackingTouch(seekBar: SeekBar) {
                continueReceivingTempAndVoltageData = false
            }

            override fun onStopTrackingTouch(seekBar: SeekBar) {
                continueReceivingTempAndVoltageData = true
            }
        }

    private val seekBar1ChangeListener: OnSeekBarChangeListener = object : OnSeekBarChangeListener {
        //private int OldProgress = progress;
        override fun onProgressChanged(seekBar: SeekBar, progress: Int, fromUser: Boolean) {

            // if (oldProgress <= progress) {
            val sb = StringBuilder("LED ALL NC ")
            val pwmValue = 1000 + 64000 * progress / 200 //120 * progress;
            sb.append(pwmValue)
            Log.i(LOG_TAG, "progress $sb")
            UDPClient.client(this@MainActivity, sb.toString())
            oldProgress = progress
        }

        override fun onStartTrackingTouch(seekBar: SeekBar) {
            continueReceivingTempAndVoltageData = false
        }

        override fun onStopTrackingTouch(seekBar: SeekBar) {
            continueReceivingTempAndVoltageData = true
        }
    }

    var psuOnClickListener = View.OnClickListener {
        continueReceivingTempAndVoltageData = false
        if (psu!!.text == PSU_OFF) {
            psu!!.text = PSU_ON
            UDPClient.client(this, PSU_ON)
        } else {
            UDPClient.client(this, PSU_OFF)
            psu!!.text = PSU_OFF
        }
        continueReceivingTempAndVoltageData = true
    }

    var ledOnClickListener =
        View.OnClickListener { v ->
            continueReceivingTempAndVoltageData = false
            val id = v.contentDescription.toString().toInt()
            Log.i(LOG_TAG, "id $id")
            when (id) {
                1 -> {
                    try {
                        led1Toogle = if (!led1Toogle) {
                            Log.i(LOG_TAG, "onClick() false")
                            UDPClient.client(this,"LED 1 1")
                            true
                        } else {
                            Log.i(LOG_TAG, "onClick() true")
                            UDPClient.client(this,"LED 1 0")
                            false
                        }
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }
                }

                2 -> {
                    try {
                        led2Toogle = if (!led2Toogle) {
                            Log.i(LOG_TAG, "onClick() false")
                            UDPClient.client(this, "LED 2 1")
                            true
                        } else {
                            Log.i(LOG_TAG, "onClick() true")
                            UDPClient.client(this, "LED 2 0")
                            false
                        }
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }
                }

                3 -> {
                    try {
                        led3Toogle = if (!led3Toogle) {
                            Log.i(LOG_TAG, "onClick() false")
                            UDPClient.client(this, "LED 3 1")
                            true
                        } else {
                            Log.i(LOG_TAG, "onClick() true")
                            UDPClient.client(this, "LED 3 0")
                            false
                        }
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }
                }

                4 -> {
                    try {
                        led4Toogle = if (!led4Toogle) {
                            Log.i(LOG_TAG, "onClick() false")
                            UDPClient.client(this, "LED 4 1")
                            true
                        } else {
                            Log.i(LOG_TAG, "onClick() true")
                            UDPClient.client(this, "LED 4 0")
                            false
                        }
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }
                }
            }
            continueReceivingTempAndVoltageData = true
        }

    private fun wait500msAndContinueReceivingData() {
        object : AsyncTask<Void?, Void?, Void?>() {
            override fun doInBackground(vararg p0: Void?): Void? {
                try {
                    Thread.sleep(500)
                } catch (e: InterruptedException) {
                    e.printStackTrace()
                }
                continueReceivingTempAndVoltageData = true
                return null
            }
        }.execute()
    }
}