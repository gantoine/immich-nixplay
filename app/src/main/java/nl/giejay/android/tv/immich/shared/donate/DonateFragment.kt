package nl.giejay.android.tv.immich.shared.donate

import android.graphics.drawable.Drawable
import android.os.Bundle
import androidx.leanback.app.GuidedStepSupportFragment
import androidx.leanback.widget.GuidanceStylist
import androidx.leanback.widget.GuidedAction
import androidx.navigation.fragment.findNavController
import nl.giejay.android.tv.immich.R
import timber.log.Timber
import java.lang.IllegalStateException

/**
 * Donations rely on Google Play Billing, which is unavailable on the Nixplay frame build
 * (no Google Play Services, and recent Billing libraries require API 21+). This fragment is
 * kept as a stub so the existing navigation/menu entry resolves; it just informs the user.
 */
class DonateFragment : GuidedStepSupportFragment() {

    override fun onCreateGuidance(savedInstanceState: Bundle?): GuidanceStylist.Guidance {
        val icon: Drawable = requireContext().getDrawable(R.drawable.icon)!!
        return GuidanceStylist.Guidance(
            getString(R.string.donation_title),
            getString(R.string.donation_unavailable),
            "",
            icon
        )
    }

    override fun onCreateActions(actions: MutableList<GuidedAction>, savedInstanceState: Bundle?) {
        actions.add(
            GuidedAction.Builder(requireContext())
                .id(0)
                .title(android.R.string.ok)
                .build()
        )
    }

    override fun onGuidedActionClicked(action: GuidedAction) {
        try {
            findNavController().popBackStack()
        } catch (e: IllegalStateException) {
            Timber.e(e, "Could not close Donate fragment")
        }
    }
}
