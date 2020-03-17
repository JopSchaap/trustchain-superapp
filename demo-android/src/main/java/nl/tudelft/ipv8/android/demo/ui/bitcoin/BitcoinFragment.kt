package nl.tudelft.ipv8.android.demo.ui.bitcoin

import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import com.google.common.util.concurrent.Service.State.RUNNING
import kotlinx.android.synthetic.main.fragment_bitcoin.*
import nl.tudelft.ipv8.android.demo.R
import nl.tudelft.ipv8.android.demo.coin.BitcoinNetworkOptions
import nl.tudelft.ipv8.android.demo.coin.SerializedDeterminsticKey
import nl.tudelft.ipv8.android.demo.coin.WalletManagerAndroid
import nl.tudelft.ipv8.android.demo.coin.WalletManagerConfiguration
import nl.tudelft.ipv8.android.demo.ui.BaseFragment
import org.bitcoinj.core.Coin
import org.bitcoinj.core.ECKey
import org.bitcoinj.core.listeners.DownloadProgressTracker
import java.util.*

/**
 * A simple [Fragment] subclass.
 * Use the [BitcoinFragment.newInstance] factory method to
 * create an instance of this fragment.
 */
class BitcoinFragment(
    override val controller: BitcoinViewController
) : BitcoinView, BaseFragment(R.layout.fragment_bitcoin) {

    override fun onActivityCreated(savedInstanceState: Bundle?) {
        super.onActivityCreated(savedInstanceState)
        initClickListeners()
    }

    private fun initClickListeners() {
        show_wallet_button.setOnClickListener {
            controller.showView("MySharedWalletsFragment")
        }

        create_wallet_button.setOnClickListener {
            controller.showView("CreateSWFragment")
        }

        search_wallet_button.setOnClickListener {
            controller.showView("JoinNetworkFragment")
        }

        startWalletButtonExisting.setOnClickListener {
            val config = WalletManagerConfiguration(
                BitcoinNetworkOptions.TEST_NET
            )
            WalletManagerAndroid.Factory(this.requireContext().applicationContext)
                .setConfiguration(config)
                .init()
            refresh()
        }

        startWalletButtonImportDefaultKey.setOnClickListener {
            val config = WalletManagerConfiguration(
                BitcoinNetworkOptions.TEST_NET,
                SerializedDeterminsticKey(
                    "spell seat genius horn argue family steel buyer spawn chef guard vast",
                    1583488954L
                )
            )

            val tracker: DownloadProgressTracker = object : DownloadProgressTracker() {
                override fun progress(
                    pct: Double,
                    blocksSoFar: Int,
                    date: Date?
                ) {
                    super.progress(pct, blocksSoFar, date)
                    val percentage = pct.toInt()
                    println("Progress: $percentage")
                    Log.i("Coin", "Progress 2: $percentage")
                    progressField.text = "Progress: $percentage"
                }

                override fun doneDownload() {
                    super.doneDownload()
                    Log.w("Coin", "Download Complete!")
                    progressField.text = "Progress: up-to-date"
                }

            }

            WalletManagerAndroid.Factory(this.requireContext().applicationContext)
                .setConfiguration(config)
                .init(tracker)

            refresh()

        }

        refreshButton.setOnClickListener {
            refresh()
        }

        generateRandomHexes.setOnClickListener {
            val key = ECKey()
            publicKeyHexes.setText("${publicKeyHexes.text}${System.lineSeparator()}${key.publicKeyAsHex}")
        }

        createMultisig.setOnClickListener {
            Log.i("Coin", "Coin: createMultisig clicked.")
            val walletManager = WalletManagerAndroid.getInstance()

            val myKey = walletManager.networkPublicECKeyHex()
            val lines = publicKeyHexes.text.lines()
            val value = coinValue.text.toString().toLong()
            val threshHold = threshHoldText.text.toString().toInt()

            val keys = lines.toMutableList()
            keys.add(myKey)
            keys.removeAt(0)

            Log.i("Coin", "Coin: your key: ${myKey}")
            Log.i("Coin", "Coin: all keys:")
            keys.forEach { key ->
                Log.i("Coin", "Coin: ${key}}")
            }
            Log.i("Coin", "Coin: value (satoshi) sending: ${value}")

            Log.i("Coin", "Coin: createMultisig, starting process.")
            val result = walletManager.startNewWalletProcess(
                keys,
                Coin.valueOf(value),
                threshHold
            )
            Log.i("Coin", "Coin: createMultisig, finished process.")

            Log.i("Coin", "Coin: createMultisig, transactionID = ${result.transactionId}")
            Log.i("Coin", "Coin: createMultisig, serialized = ${result.serializedTransaction}")
            multisigOutputText.setText(result.transactionId)

        }

    }

    fun refresh() {
        val walletManager = WalletManagerAndroid.getInstance()
        walletStatus.text = "Status: ${walletManager.kit.state()}"
        walletBalance.text =
            "Bitcoin available: ${walletManager.kit.wallet().balance.toFriendlyString()}"
        chosenNetwork.text = "Network: ${walletManager.params.id}"
        val seed = walletManager.toSeed()
        walletSeed.text = "Seed: ${seed.seed}, ${seed.creationTime}"
        yourPublicHex.text = "Public (Protocol) Key: ${walletManager.networkPublicECKeyHex()}"

        if (walletManager.kit.state().equals(RUNNING)) {
            startWalletButtonExisting.isEnabled = false
            startWalletButtonExisting.isClickable = false
            startWalletButtonImportDefaultKey.isEnabled = false
            startWalletButtonImportDefaultKey.isClickable = false

            generateRandomHexes.isEnabled = true
            createMultisig.isEnabled = true
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        // Inflate the layout for this fragment
        return inflater.inflate(R.layout.fragment_bitcoin, container, false)
    }

    companion object {
        /**
         * Use this factory method to create a new instance of
         * this fragment using the provided parameters.
         *
         * @return A new instance of fragment bitcoinFragment.
         */
        @JvmStatic
        fun newInstance(controller: BitcoinViewController) = BitcoinFragment(controller)
    }
}
