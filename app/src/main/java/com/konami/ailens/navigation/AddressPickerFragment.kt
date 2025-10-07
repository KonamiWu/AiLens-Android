package com.konami.ailens.navigation

import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.util.AttributeSet
import android.util.Log
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.animation.Interpolator
import android.widget.ImageView
import android.widget.TextView
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.constraintlayout.widget.ConstraintSet
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.doOnLayout
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import com.konami.ailens.orchestrator.Orchestrator
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.gms.maps.model.LatLng
import com.konami.ailens.R
import com.konami.ailens.databinding.FragmentAddressPickerBinding
import com.konami.ailens.resolveAttrColor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import kotlin.math.pow

class AddressPickerFragment: Fragment(), NavigationMapFragment.Callbacks {
    class SmoothInterpolator : Interpolator {
        override fun getInterpolation(t: Float): Float {
            return (1 - (1 - t).toDouble().pow(3.0)).toFloat()
        }
    }

    private lateinit var recyclerView: RecyclerView
    private lateinit var autoCompleteRecyclerView: RecyclerView
    private lateinit var autoCompleteAdapter: AutoCompleteAdapter

    lateinit var state: LayoutState

    lateinit var binding: FragmentAddressPickerBinding
    var topMargin = 0f
    var topInset = 0
    private var selectedMode: Orchestrator.TravelMode = Orchestrator.TravelMode.DRIVING
    private var navDisplay: com.konami.ailens.orchestrator.capability.NavigationDisplayCapability? = null
    
    // ViewModel shared with NavigationMapFragment
    private lateinit var navigationMapViewModel: NavigationMapViewModel
    private lateinit var addressPickerViewModel: AddressPickerViewModel

    var slidingDistance = 0f
    @SuppressLint("ClickableViewAccessibility")
    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        topMargin = resources.getDimension(R.dimen.address_picker_margin_top)
        binding = FragmentAddressPickerBinding.inflate(inflater, container, false)
        ViewCompat.setOnApplyWindowInsetsListener(binding.root) { view, insets ->
            topInset = insets.getInsets(WindowInsetsCompat.Type.systemBars()).top
//            binding.main.setPadding(
//                view.paddingLeft,
//                topInset,
//                view.paddingRight,
//                view.paddingBottom
//            )
            
            // Adjust back button top margin to account for status bar
            val layoutParams = binding.backButton.layoutParams as androidx.constraintlayout.widget.ConstraintLayout.LayoutParams
            val baseMargin = resources.getDimension(R.dimen.address_picker_back_button_margin_top).toInt()
            layoutParams.topMargin = baseMargin + topInset
            binding.backButton.layoutParams = layoutParams
            
            insets
        }
        
        // Initialize shared ViewModel
        navigationMapViewModel = ViewModelProvider(this).get(NavigationMapViewModel::class.java)
        addressPickerViewModel = ViewModelProvider(this).get(AddressPickerViewModel::class.java)
        
        state = AddressState(this, false)

        // Embed dedicated map fragment for pre-navigation planning
        val tag = "nav_map_fragment"
        if (childFragmentManager.findFragmentByTag(tag) == null) {
            childFragmentManager.beginTransaction()
                .replace(R.id.mapContainer, NavigationMapFragment(), tag)
                .commitNow()
        }

        // Remove NavigationDisplayCapability from this screen; will use ViewModel data instead

        // transport mode toggles
        binding.walkingLayout.setOnClickListener {
            updateSelectedMode(Orchestrator.TravelMode.WALKING)
        }
        binding.motorcycleLayout.setOnClickListener {
            updateSelectedMode(Orchestrator.TravelMode.MOTORCYCLE)
        }
        binding.drivingLayout.setOnClickListener {
            updateSelectedMode(Orchestrator.TravelMode.DRIVING)
        }

        recyclerView = binding.favoriteRecyclerView
        autoCompleteRecyclerView = binding.autoCompleteRecyclerView
        
        // Setup AutoComplete RecyclerView
        autoCompleteAdapter = AutoCompleteAdapter { prediction ->
            // Handle prediction selection
            addressPickerViewModel.getPlaceDetails(prediction.placeId) { lat, lng, address ->
                binding.destinationEditText.setText(address)
                addressPickerViewModel.clearAutoComplete()
                // Trigger navigation like map tap
                navigationMapViewModel.handleMapTap(
                    LatLng(lat, lng),
                    selectedMode
                )
            }
        }
        autoCompleteRecyclerView.layoutManager = LinearLayoutManager(requireContext())
        autoCompleteRecyclerView.adapter = autoCompleteAdapter
        
        // Setup TextWatcher for destinationEditText
        binding.destinationEditText.addTextChangedListener(object : TextWatcher {
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

        // Ensure keyboard shows when EditText gains focus (first tap) and expand AddressState
        binding.destinationEditText.setOnFocusChangeListener { v, hasFocus ->
            if (hasFocus) {
                v.post {
                    val imm = v.context.getSystemService(Context.INPUT_METHOD_SERVICE) as android.view.inputmethod.InputMethodManager
                    imm.showSoftInput(v, android.view.inputmethod.InputMethodManager.SHOW_IMPLICIT)
                }
                (state as? AddressState)?.requestExpand()
            }
        }
        
        // Observe AutoComplete predictions
        lifecycleScope.launch {
            addressPickerViewModel.autoCompletePredictions.collect { predictions ->
                autoCompleteAdapter.updatePredictions(predictions)
                autoCompleteRecyclerView.visibility = if (predictions.isEmpty()) View.GONE else View.VISIBLE
            }
        }
        

        val manager = LinearLayoutManager(requireContext())
        manager.orientation = LinearLayoutManager.HORIZONTAL
        recyclerView.layoutManager = manager
        recyclerView.adapter = FavoriteAdapter(requireContext()) { value ->
            if (value == 0)
                state = NavState(this)
        }

        val density = resources.displayMetrics.density
        val borderColor = requireContext().resolveAttrColor(R.attr.appBorderDarkGray)
        val divider = FavoriteDivider(
            space = (4 * density).toInt(),
            lineWidth = (1 * density).toInt(),
            lineColor = borderColor
        )
        recyclerView.addItemDecoration(divider)

        binding.startButton.setOnClickListener {
            state = AddressState(this, true)
            val destination = binding.destinationEditText.text?.toString()?.trim().orEmpty()
            if (destination.isNotEmpty()) {
                val mode = when (selectedMode) {
                    Orchestrator.TravelMode.WALKING -> "walking"
                    Orchestrator.TravelMode.MOTORCYCLE -> "motorcycle"
                    else -> "driving"
                }
                Orchestrator.instance.handleNavigation(destination, mode, null)
                // Navigate to guidance fragment that uses service-owned map
                findNavController().navigate(R.id.navigationGuidanceFragment)
            }
        }

        // Back button click handler
        binding.backButton.setOnClickListener {
            // Clear the route from map
            navigationMapViewModel.clearRoute()
            
            // Reset to AddressState (hide the route and back button)
            state = AddressState(this, true)
            binding.destinationEditText.text?.clear()
        }

        binding.destinationLayout.doOnLayout {
            layout()
            binding.main.post {
                (state as? AddressState)?.updateLayout()
            }
        }

        binding.container.setOnTouchListener { view, event ->
            state.onTouch(view, event)
        }
        // Allow dragging to start from child views as well
        binding.autoCompleteRecyclerView.setOnTouchListener { view, event ->
            state.onTouch(view, event)
        }
        binding.destinationEditText.setOnTouchListener { view, event ->
            state.onTouch(view, event)
        }
        binding.favoriteRecyclerView.setOnTouchListener { view, event ->
            state.onTouch(view, event)
        }

        return binding.root
    }

    override fun onDestroyView() {
        super.onDestroyView()
        navDisplay?.let { Orchestrator.instance.removeNavigationDisplay(it) }
        navDisplay = null
    }

    // NavigationMapFragment.Callbacks
    override fun getSelectedMode(): Orchestrator.TravelMode = selectedMode

    override fun onDestinationSelected(address: String) {
        binding.destinationEditText.setText(address)
        state = NavState(this)
    }
    override fun onRouteSummary(durationText: String, distanceText: String) {
        if (durationText.isEmpty() && distanceText.isEmpty()) {
            // Route cleared - hide time info
            binding.timeTextView.text = ""
        } else {
            binding.timeTextView.text = "$durationText ($distanceText)"
        }
    }
    
    override fun getContainerHeight(): Int {
        return binding.container.height
    }
    
    override fun getNavContainerHeight(): Int {
        return navContainerHeight()
    }

    private fun updateSelectedMode(mode: Orchestrator.TravelMode) {
        selectedMode = mode
        // move indicator under selected mode
        val targetId = when(mode) {
            Orchestrator.TravelMode.WALKING -> R.id.walkingLayout
            Orchestrator.TravelMode.MOTORCYCLE -> R.id.motorcycleLayout
            Orchestrator.TravelMode.DRIVING -> R.id.drivingLayout
        }
        val header = binding.transportLayout.getChildAt(0) as ConstraintLayout
        val set = ConstraintSet()
        set.clone(header)
        set.clear(R.id.indicatorView, ConstraintSet.START)
        set.clear(R.id.indicatorView, ConstraintSet.END)
        set.connect(R.id.indicatorView, ConstraintSet.START, targetId, ConstraintSet.START)
        set.connect(R.id.indicatorView, ConstraintSet.END, targetId, ConstraintSet.END)
        set.connect(R.id.indicatorView, ConstraintSet.BOTTOM, ConstraintSet.PARENT_ID, ConstraintSet.BOTTOM)
        set.applyTo(header)

        // Re-calculate route with new travel mode if destination is already set
        val destination = binding.destinationEditText.text?.toString()?.trim()
        if (!destination.isNullOrEmpty()) {
            // Get current route destination and recalculate with new mode
            val currentRoute = navigationMapViewModel.route.value
            if (currentRoute != null) {
                // Re-trigger route calculation with new mode
                navigationMapViewModel.handleMapTap(currentRoute.destination, mode)
            }
        }
    }

    private fun layout() {
        val insets = ViewCompat.getRootWindowInsets(binding.root)
        val navBarInset = insets?.getInsets(WindowInsetsCompat.Type.navigationBars())?.bottom ?: 0
        val topPadding = resources.getDimension(R.dimen.address_picker_padding_top)
        val editTextHeight = resources.getDimension(R.dimen.address_picker_edit_text_height)
        val favoriteRecyclerHeight = resources.getDimension(R.dimen.address_picker_favorite_height).toInt()
        val favoriteRecyclerViewMarginTop = resources.getDimension(R.dimen.address_picker_favorite_margin_top).toInt()
        val favoriteRecyclerViewMarginBottom = resources.getDimension(R.dimen.address_picker_favorite_margin_bottom).toInt()
        val height = topPadding + editTextHeight + favoriteRecyclerHeight + favoriteRecyclerViewMarginTop + favoriteRecyclerViewMarginBottom
        val target = resources.displayMetrics.heightPixels - height - navBarInset - topInset

        slidingDistance = target

        val spacing = resources.getDimension(R.dimen.address_picker_edit_text_spacing)
        val margin = spacing - editTextHeight

        val set = ConstraintSet()
        set.clone(binding.main)
        set.setMargin(R.id.container, ConstraintSet.TOP, target.toInt())
        binding.myLocationLayout.visibility = View.VISIBLE
        set.connect(R.id.destinationLayout, ConstraintSet.TOP, R.id.myLocationLayout, ConstraintSet.BOTTOM, margin.toInt())

        set.connect(R.id.transportLayout, ConstraintSet.BOTTOM, ConstraintSet.PARENT_ID, ConstraintSet.BOTTOM)

        set.applyTo(binding.main)
    }

    fun navContainerHeight() : Int {
        val insets = ViewCompat.getRootWindowInsets(binding.root)
        val navBarInset = insets?.getInsets(WindowInsetsCompat.Type.navigationBars())?.bottom ?: 0f
        val topPadding = resources.getDimension(R.dimen.address_picker_padding_top)
        val editTextHeight = resources.getDimension(R.dimen.address_picker_edit_text_height)
        val transportLayoutTopMargin = resources.getDimension(R.dimen.address_picker_transport_mode_margin_top)
        val transportLayoutHeight = resources.getDimension(R.dimen.address_picker_nav_mode_layout_height)
        val timeMarginTop = resources.getDimension(R.dimen.address_picker_time_margin_top)
        val timeHeight = resources.getDimension(R.dimen.address_picker_time_time_height)
        val startMarginTop = resources.getDimension(R.dimen.address_picker_stat_button_margin_top)
        val startMarginBottom = resources.getDimension(R.dimen.address_picker_stat_button_margin_bottom)
        val startHeight = resources.getDimension(R.dimen.address_picker_time_start_button_height)

        val result = (navBarInset.toFloat() + topPadding + editTextHeight + transportLayoutTopMargin + transportLayoutHeight + timeMarginTop + timeHeight + startMarginTop + startHeight + startMarginBottom).toInt()

        return result
    }

    class FavoriteAdapter(val context: Context, val onClick: (Int) -> Unit): RecyclerView.Adapter<FavoriteAdapter.ViewHolder>() {
        class ViewHolder(view: View): RecyclerView.ViewHolder(view) {
            val imageView = view.findViewById<ImageView>(R.id.imageView)
            val textView = view.findViewById<TextView>(R.id.textView)
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
            val view = LayoutInflater.from(parent.context).inflate(R.layout.favorite_item, parent, false)
            return ViewHolder(view)
        }

        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            holder.textView.setOnClickListener {
                onClick.invoke(position)
            }
            if (position == 0) {
                holder.imageView.setImageResource(R.drawable.ic_favorite_home)
                holder.textView.text = context.resources.getString(R.string.favorite_home)
            } else if (position == 1) {
                holder.imageView.setImageResource(R.drawable.ic_favorite_company)
                holder.textView.text = context.resources.getString(R.string.favorite_company)
            } else {
                holder.imageView.setImageResource(R.drawable.ic_favorite_edit)
            }
        }

        override fun getItemCount(): Int {
            return 3
        }
    }
    
    class AutoCompleteAdapter(private val onItemClick: (AutoCompletePrediction) -> Unit) : 
        RecyclerView.Adapter<AutoCompleteAdapter.ViewHolder>() {
        
        private var predictions = listOf<AutoCompletePrediction>()
        
        class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
            val mainText: TextView = view.findViewById(R.id.primaryTextView)
            val secondaryText: TextView = view.findViewById(R.id.secondaryTextView)
        }
        
        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
            val view = LayoutInflater.from(parent.context)
                .inflate(R.layout.auto_complete_item, parent, false)
            return ViewHolder(view)
        }
        
        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            val prediction = predictions[position]
            holder.mainText.text = prediction.mainText
            holder.secondaryText.text = prediction.secondaryText
            
            holder.itemView.setOnClickListener {
                onItemClick(prediction)
            }
        }
        
        override fun getItemCount() = predictions.size
        
        fun updatePredictions(newPredictions: List<AutoCompletePrediction>) {
            predictions = newPredictions
            notifyDataSetChanged()
        }
    }
}
