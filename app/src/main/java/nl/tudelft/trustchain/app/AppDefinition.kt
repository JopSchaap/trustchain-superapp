package nl.tudelft.trustchain.app

import android.app.Activity
import android.content.Context
import android.content.Intent
import androidx.annotation.ColorRes
import androidx.annotation.DrawableRes
import nl.tudelft.trustchain.musicdao.MusicActivity
import nl.tudelft.trustchain.foc.MainActivityFOC
import nl.tudelft.trustchain.common.R
import nl.tudelft.trustchain.currencyii.CurrencyIIMainActivity
import nl.tudelft.trustchain.debug.DebugActivity
import nl.tudelft.trustchain.eurotoken.EuroTokenMainActivity
import nl.tudelft.trustchain.valuetransfer.ValueTransferMainActivity
import nl.tudelft.trustchain.peerai.PeerAIActivity

open class AppDefinition(
    @DrawableRes val icon: Int,
    val appName: String,
    @ColorRes val color: Int,
    val activity: Class<out Activity>,
    val disableImageTint: Boolean = false,
) {
    open fun getIntent(context: Context): Intent {
        return Intent(context, this.activity)
    }

    companion object Color {
        fun create(
            name: String,
            javaClass: Class<out Activity>
        ): AppDefinition {
            return AppDefinition(icon, name, color, javaClass)
        }

        @DrawableRes val icon = R.drawable.ic_bug_report_black_24dp

        @ColorRes val color = R.color.dark_gray
    }

    enum class BaseAppDefinitions(val appDefinition: AppDefinition) {
        CURRENCY_II(
            AppDefinition(
                R.drawable.ic_baseline_how_to_vote_24,
                "On-Chain Democracy",
                R.color.democracy_blue,
                CurrencyIIMainActivity::class.java,
            )
        ),
        DEBUG(
            AppDefinition(
                R.drawable.ic_bug_report_black_24dp,
                "Debug",
                R.color.dark_gray,
                DebugActivity::class.java
            )
        ),
        VALUETRANSFER(
            AppDefinition(
                R.drawable.ic_idelft_logo,
                "IDelft",
                R.color.colorPrimaryValueTransfer,
                ValueTransferMainActivity::class.java,
                true,
            )
        ),
        EUROTOKEN(
            AppDefinition(
                R.drawable.ic_baseline_euro_symbol_24,
                "EuroToken",
                R.color.metallic_gold,
                EuroTokenMainActivity::class.java
            )
        ),
        MUSIC_DAO(
            AppDefinition(
                android.R.drawable.ic_media_play,
                "MusicDAO",
                R.color.black,
                MusicActivity::class.java
            )
        ),
        FREEDOM_OF_COMPUTING(
            AppDefinition(
                R.drawable.ic_naruto,
                "Freedom of Computing",
                R.color.blue,
                MainActivityFOC::class.java
            )
        ),
        PeerAi(
            AppDefinition(
                R.drawable.ic_book_hover_over_hand,
                "PeerAI",
                R.color.green,
                PeerAIActivity::class.java
            )
        )
    }
}
