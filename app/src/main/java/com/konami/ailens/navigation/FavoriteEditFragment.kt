package com.konami.ailens.navigation

import android.content.Context
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.InputMethodManager
import androidx.core.view.ViewCompat
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import com.konami.ailens.R
import com.konami.ailens.SharedPrefs
import com.konami.ailens.databinding.FragmentFavoriteEditBinding
import com.konami.ailens.resolveAttrColor
import kotlinx.coroutines.launch

class FavoriteEditFragment : Fragment() {
    private lateinit var binding: FragmentFavoriteEditBinding
    private lateinit var addressPickerViewModel: AddressPickerViewModel
    private lateinit var autoCompleteAdapter: AddressPickerFragment.AutoCompleteAdapter
    private var favoriteType: FavoriteListFragment.FavoriteType = FavoriteListFragment.FavoriteType.HOME
    private var value: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Get the favorite type from arguments
        val typeOrdinal = arguments?.getInt("favoriteTypeOrdinal") ?: 0
        favoriteType = FavoriteListFragment.FavoriteType.entries.toTypedArray()[typeOrdinal]
        value = arguments?.getString("value")
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        binding = FragmentFavoriteEditBinding.inflate(inflater, container, false)

        // Handle window insets manually
        ViewCompat.setOnApplyWindowInsetsListener(binding.root) { view, insets ->
            val systemBars = insets.getInsets(androidx.core.view.WindowInsetsCompat.Type.systemBars())
            val basePaddingTop = resources.getDimension(com.konami.ailens.R.dimen.favorite_edit_padding_top).toInt()
            view.setPadding(
                view.paddingLeft,
                systemBars.top + basePaddingTop,
                view.paddingRight,
                view.paddingBottom
            )
            insets
        }

        // Initialize ViewModel
        addressPickerViewModel = ViewModelProvider(this).get(AddressPickerViewModel::class.java)

        // Setup hint and icon based on favorite type
        when (favoriteType) {
            FavoriteListFragment.FavoriteType.HOME -> {
                binding.addressEditText.hint = "Set your home address"
                binding.favoriteIcon.setImageResource(R.drawable.ic_favorite_list_home_active)
            }
            FavoriteListFragment.FavoriteType.COMPANY -> {
                binding.addressEditText.hint = "Set your company address"
                binding.favoriteIcon.setImageResource(R.drawable.ic_favorite_list_company_active)
            }
        }

        // Setup back button
        binding.backButton.setOnClickListener {
            findNavController().navigateUp()
        }

        // Setup AutoComplete RecyclerView
        autoCompleteAdapter = AddressPickerFragment.AutoCompleteAdapter { prediction ->
            // If prediction has coordinates (from history), use them directly
            if (prediction.lat != null && prediction.lng != null) {
                // Save to SharedPrefs
                val favoriteLocation = com.konami.ailens.FavoriteLocation(
                    lat = prediction.lat,
                    lng = prediction.lng,
                    mainText = prediction.mainText,
                    secondaryText = prediction.secondaryText
                )
                when (favoriteType) {
                    FavoriteListFragment.FavoriteType.HOME -> SharedPrefs.instance.homeFavorite = favoriteLocation
                    FavoriteListFragment.FavoriteType.COMPANY -> SharedPrefs.instance.companyFavorite = favoriteLocation
                }
                // Navigate back
                findNavController().navigateUp()
            } else if (prediction.placeId != null) {
                // If prediction has placeId (from API), get place details first
                addressPickerViewModel.getPlaceDetails(prediction.placeId) { lat, lng, address ->
                    // Add to history after getting coordinates
                    val historyItem = com.konami.ailens.AddressHistoryItem(
                        lat = lat,
                        lng = lng,
                        mainText = prediction.mainText,
                        secondaryText = prediction.secondaryText
                    )
                    SharedPrefs.instance.addAddressToHistory(historyItem)

                    // Save to SharedPrefs
                    val favoriteLocation = com.konami.ailens.FavoriteLocation(
                        lat = lat,
                        lng = lng,
                        mainText = prediction.mainText,
                        secondaryText = prediction.secondaryText
                    )
                    when (favoriteType) {
                        FavoriteListFragment.FavoriteType.HOME -> SharedPrefs.instance.homeFavorite = favoriteLocation
                        FavoriteListFragment.FavoriteType.COMPANY -> SharedPrefs.instance.companyFavorite = favoriteLocation
                    }

                    // Navigate back
                    findNavController().navigateUp()
                }
            }
        }
        binding.autoCompleteRecyclerView.adapter = autoCompleteAdapter

        val density = resources.displayMetrics.density
        val dividerHeight = (1 * density).toInt()
        val paddingHorizontal = resources.getDimension(R.dimen.favorite_list_item_padding_horizontal).toInt()
        val dividerColor = requireContext().resolveAttrColor(R.attr.appBorderDarkGray)
        val divider = VerticalDivider(
            height = dividerHeight,
            lineColor = dividerColor,
            paddingStart = paddingHorizontal,
            paddingEnd = paddingHorizontal
        )
        binding.autoCompleteRecyclerView.addItemDecoration(divider)

        binding.addressEditText.setText(value)
        // Setup TextWatcher for addressEditText
        binding.addressEditText.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                val query = s?.toString()?.trim() ?: ""
                if (query.isNotEmpty()) {
                    addressPickerViewModel.searchAutoComplete(query)
                } else {
                    addressPickerViewModel.clearAutoComplete()
                }
            }
        })

        // Observe AutoComplete predictions
        lifecycleScope.launch {
            addressPickerViewModel.autoCompletePredictions.collect { predictions ->
                autoCompleteAdapter.updatePredictions(predictions)
            }
        }

        // Auto-focus EditText and show keyboard
        binding.addressEditText.requestFocus()
        binding.addressEditText.post {
            val imm = requireContext().getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
            imm.showSoftInput(binding.addressEditText, InputMethodManager.SHOW_IMPLICIT)
        }

        return binding.root
    }

    override fun onDestroyView() {
        super.onDestroyView()
        // Hide keyboard
        val imm = requireContext().getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
        imm.hideSoftInputFromWindow(binding.addressEditText.windowToken, 0)
    }
}
