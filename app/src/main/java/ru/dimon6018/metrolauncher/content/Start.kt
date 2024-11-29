package ru.dimon6018.metrolauncher.content

import android.annotation.SuppressLint
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.res.Configuration
import android.content.res.Resources
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.util.Log
import android.view.GestureDetector
import android.view.GestureDetector.SimpleOnGestureListener
import android.view.Gravity
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.animation.AccelerateInterpolator
import android.view.animation.LinearInterpolator
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.PopupWindow
import androidx.activity.OnBackPressedCallback
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updatePadding
import androidx.fragment.app.DialogFragment
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.RecyclerView
import coil3.load
import coil3.request.ErrorResult
import coil3.request.ImageRequest
import com.arasthel.spannedgridlayoutmanager.SpanSize
import com.arasthel.spannedgridlayoutmanager.SpannedGridLayoutManager
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.card.MaterialCardView
import com.google.android.material.textfield.TextInputLayout
import com.google.android.material.textview.MaterialTextView
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import ru.dimon6018.metrolauncher.Application.Companion.PREFS
import ru.dimon6018.metrolauncher.Application.Companion.customFont
import ru.dimon6018.metrolauncher.Application.Companion.customLightFont
import ru.dimon6018.metrolauncher.Application.Companion.isAppOpened
import ru.dimon6018.metrolauncher.Application.Companion.isStartMenuOpened
import ru.dimon6018.metrolauncher.Main
import ru.dimon6018.metrolauncher.Main.Companion.isLandscape
import ru.dimon6018.metrolauncher.Main.Companion.isViewPagerScrolling
import ru.dimon6018.metrolauncher.MainViewModel
import ru.dimon6018.metrolauncher.R
import ru.dimon6018.metrolauncher.content.data.app.App
import ru.dimon6018.metrolauncher.content.data.tile.Tile
import ru.dimon6018.metrolauncher.content.data.tile.TileDao
import ru.dimon6018.metrolauncher.content.settings.SettingsActivity
import ru.dimon6018.metrolauncher.databinding.LauncherStartScreenBinding
import ru.dimon6018.metrolauncher.databinding.SpaceTileBinding
import ru.dimon6018.metrolauncher.databinding.TileBinding
import ru.dimon6018.metrolauncher.helpers.dragndrop.ItemTouchCallback
import ru.dimon6018.metrolauncher.helpers.dragndrop.ItemTouchHelperAdapter
import ru.dimon6018.metrolauncher.helpers.dragndrop.ItemTouchHelperViewHolder
import ru.dimon6018.metrolauncher.helpers.dragndrop.OnStartDragListener
import ru.dimon6018.metrolauncher.helpers.receivers.PackageChangesReceiver
import ru.dimon6018.metrolauncher.helpers.ui.StartRecyclerView
import ru.dimon6018.metrolauncher.helpers.utils.Utils
import ru.dimon6018.metrolauncher.helpers.utils.Utils.Companion.accentColorFromPrefs
import ru.dimon6018.metrolauncher.helpers.utils.Utils.Companion.getTileColorFromPrefs
import ru.dimon6018.metrolauncher.helpers.utils.Utils.Companion.getTileColorName
import ru.dimon6018.metrolauncher.helpers.utils.Utils.Companion.isScreenOn
import ru.dimon6018.metrolauncher.helpers.utils.Utils.MarginItemDecoration
import kotlin.random.Random

// Start screen
class Start : Fragment(), OnStartDragListener {

    private lateinit var mItemTouchHelper: ItemTouchHelper

    private val defaultDispatcher: CoroutineDispatcher = Dispatchers.Default
    private val mainDispatcher: CoroutineDispatcher = Dispatchers.Main
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO

    private var mSpannedLayoutManager: SpannedGridLayoutManager? = null
    private var mAdapter: NewStartAdapter? = null
    private var tiles: MutableList<Tile>? = null
    private var packageBroadcastReceiver: BroadcastReceiver? = null

    private var isBroadcasterRegistered = false
    private var screenIsOn = false

    private var _binding: LauncherStartScreenBinding? = null
    private val binding get() = _binding!!
    private lateinit var mainViewModel: MainViewModel

    private var screenLoaded = false

    private val backCallback = object : OnBackPressedCallback(true) {
        override fun handleOnBackPressed() {
            mAdapter?.let { if (it.isEditMode) it.disableEditMode() }
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = LauncherStartScreenBinding.inflate(inflater, container, false)
        mainViewModel = ViewModelProvider(requireActivity())[MainViewModel::class.java]
        binding.startFrame.setOnClickListener {
            mAdapter?.let { if (it.isEditMode) it.disableEditMode() }
        }
        ViewCompat.setOnApplyWindowInsetsListener(binding.startTiles) { view, insets ->
            val systemBarInsets = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            view.updatePadding(
                bottom = systemBarInsets.bottom,
                left = systemBarInsets.left + view.paddingLeft,
                right = systemBarInsets.right + view.paddingEnd,
                top = systemBarInsets.top
            )
            insets
        }
        if (PREFS.isWallpaperEnabled) {
            binding.startTiles.isUpdateEnabled = true
        }
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        if (context != null) {
            viewLifecycleOwner.lifecycleScope.launch(defaultDispatcher) {
                tiles = mainViewModel.getTileDao().getTilesList()
                setupRecyclerViewLayoutManager(requireContext())
                setupAdapter()
                withContext(mainDispatcher) {
                    configureRecyclerView()
                    registerBroadcast()
                    screenLoaded = true
                    observe()
                }
                cancel("done")
            }
        }
    }

    private fun observe() {
        if (!screenLoaded || mainViewModel.getTileDao().getTilesLiveData()
                .hasActiveObservers()
        ) return
        mainViewModel.getTileDao().getTilesLiveData().observe(viewLifecycleOwner) {
            mAdapter ?: return@observe
            if (mAdapter!!.list != it) {
                Log.w("obs", "set new data")
                mAdapter!!.setData(it)
            }
        }
    }

    private fun stopObserver() {
        mainViewModel.getTileDao().getTilesLiveData().removeObservers(viewLifecycleOwner)
    }

    override fun onDestroyView() {
        stopObserver()
        super.onDestroyView()
        _binding = null
    }

    private fun setupAdapter() {
        mAdapter = NewStartAdapter(requireContext(), tiles!!)
        mItemTouchHelper = ItemTouchHelper(ItemTouchCallback(mAdapter!!))
    }

    private fun addCallback() {
        activity?.onBackPressedDispatcher?.addCallback(viewLifecycleOwner, backCallback)
    }

    private fun removeCallback() {
        backCallback.remove()
    }

    private fun configureRecyclerView() {
        binding.startTiles.apply {
            layoutManager = mSpannedLayoutManager
            adapter = mAdapter
            mItemTouchHelper.attachToRecyclerView(this)
            addItemDecoration(MarginItemDecoration(14))
        }
    }

    private fun setupRecyclerViewLayoutManager(context: Context?) {
        if (mSpannedLayoutManager != null) mSpannedLayoutManager = null
        if (!isLandscape) {
            // phone
            mSpannedLayoutManager = SpannedGridLayoutManager(
                orientation = RecyclerView.VERTICAL,
                rowCount = if (!PREFS.isMoreTilesEnabled) 8 else 12,
                columnCount = if (!PREFS.isMoreTilesEnabled) 4 else 6
            )
        } else {
            // Landscape orientation
            val tablet = context?.resources?.getBoolean(R.bool.isTablet) == true
            mSpannedLayoutManager = if (tablet) {
                // tablet
                SpannedGridLayoutManager(
                    orientation = RecyclerView.VERTICAL,
                    rowCount = if (!PREFS.isMoreTilesEnabled) 2 else 3,
                    columnCount = if (!PREFS.isMoreTilesEnabled) 4 else 6
                )
            } else {
                // phone but landscape
                SpannedGridLayoutManager(
                    orientation = RecyclerView.VERTICAL,
                    rowCount = 3,
                    columnCount = if (!PREFS.isMoreTilesEnabled) 4 else 6
                )
            }
        }
        mSpannedLayoutManager?.apply {
            itemOrderIsStable = true
            spanSizeLookup = SpannedGridLayoutManager.SpanSizeLookup { position ->
                when (tiles!![position].tileSize) {
                    "small" -> SpanSize(1, 1)
                    "medium" -> SpanSize(2, 2)
                    "big" -> SpanSize(4, 2)
                    else -> SpanSize(1, 1)
                }
            }
        }
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        isLandscape = newConfig.orientation == Configuration.ORIENTATION_LANDSCAPE
        setupRecyclerViewLayoutManager(context)
        binding.startTiles.apply {
            layoutManager = mSpannedLayoutManager
        }
    }

    override fun onResume() {
        if (screenLoaded) observe()
        if (!screenIsOn) {
            if (binding.startTiles.visibility == View.INVISIBLE) binding.startTiles.visibility =
                View.VISIBLE
        }
        if (isAppOpened) {
            viewLifecycleOwner.lifecycleScope.launch {
                animateTiles(false, null, null)
            }
            isAppOpened = false
        }
        isStartMenuOpened = true
        super.onResume()
        screenIsOn = isScreenOn(context)
        mAdapter?.apply {
            isBottomRight = false
            isBottomLeft = false
            isTopRight = false
            isTopLeft = false
        }
    }

    override fun onPause() {
        super.onPause()
        screenIsOn = isScreenOn(context)
        if (!screenIsOn) {
            if (binding.startTiles.visibility == View.VISIBLE) {
                binding.startTiles.visibility = View.INVISIBLE
            }
        }
        mAdapter?.apply {
            if (isEditMode) disableEditMode()
        }
        isStartMenuOpened = false
    }

    override fun onStop() {
        super.onStop()
        isStartMenuOpened = false
    }

    override fun onStartDrag(viewHolder: RecyclerView.ViewHolder?) {
        if (viewHolder != null && !PREFS.isStartBlocked && mAdapter != null) {
            if (!mAdapter!!.isEditMode) mAdapter!!.enableEditMode()
            mItemTouchHelper.startDrag(viewHolder)
        }
    }

    @SuppressLint("InlinedApi", "UnspecifiedRegisterReceiverFlag")
    private fun registerBroadcast() {
        Log.d("Start", "reg broadcaster")
        if (!isBroadcasterRegistered) {
            isBroadcasterRegistered = true
            packageBroadcastReceiver = object : BroadcastReceiver() {
                override fun onReceive(context: Context, intent: Intent) {
                    val packageName = intent.getStringExtra("package")
                    // End early if it has anything to do with us.
                    if (packageName.isNullOrEmpty()) return
                    val action = intent.getIntExtra("action", 42)
                    when (action) {
                        PackageChangesReceiver.PACKAGE_INSTALLED -> {
                            Log.d("Start", "pkg installed")
                            val bool = PREFS.iconPackPackage != "null"
                            (requireActivity() as Main).generateIcon(packageName, bool)
                            mainViewModel.addAppToList(
                                App(
                                    appLabel = context.packageManager.getApplicationInfo(
                                        packageName,
                                        0
                                    ).name,
                                    appPackage = packageName,
                                    id = Random.nextInt()
                                )
                            )
                            if (PREFS.pinNewApps) {
                                pinApp(packageName)
                            }
                        }

                        PackageChangesReceiver.PACKAGE_REMOVED -> {
                            packageName.apply { broadcastListUpdater(this, true) }
                        }

                        else -> {
                            packageName.apply { broadcastListUpdater(this, false) }
                        }
                    }
                }
            }
            IntentFilter().apply {
                addAction("ru.dimon6018.metrolauncher.PACKAGE_CHANGE_BROADCAST")
            }.also {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    requireActivity().registerReceiver(
                        packageBroadcastReceiver,
                        it,
                        Context.RECEIVER_EXPORTED
                    )
                } else {
                    requireActivity().registerReceiver(packageBroadcastReceiver, it)
                }
            }
        } else {
            Log.d("Start", "broadcaster already registered")
        }
    }

    private fun broadcastListUpdater(packageName: String, isDelete: Boolean) {
        packageName.apply {
            Log.d("Start", "update list by broadcaster")
            CoroutineScope(ioDispatcher).launch {
                var newList = mainViewModel.getTileDao().getTilesList()
                if (isDelete) {
                    newList.forEach {
                        if (it.tilePackage == packageName) {
                            Log.d("Start", "delete")
                            destroyTile(it)
                        }
                    }
                    newList = mainViewModel.getTileDao().getTilesList()
                }
                withContext(mainDispatcher) {
                    mAdapter?.setData(newList)
                }
            }
        }
    }

    private fun pinApp(packageName: String) {
        lifecycleScope.launch(ioDispatcher) {
            if (mAdapter != null) {
                var pos = 0
                for (i in 0..<mAdapter!!.list.size) {
                    if (mAdapter!!.list[i].tileType == -1) {
                        pos = i
                        break
                    }
                }
                val id = Random.nextLong(1000, 2000000)
                val item = Tile(
                    pos, id, -1, 0,
                    isSelected = false,
                    tileSize = Utils.generateRandomTileSize(true),
                    tileLabel = activity?.packageManager?.getApplicationInfo(packageName, 0)
                        ?.loadLabel(requireActivity().packageManager!!).toString(),
                    tilePackage = packageName
                )
                mainViewModel.getTileDao().addTile(item)
            }
        }
    }

    private fun unregisterBroadcast() {
        isBroadcasterRegistered = false
        packageBroadcastReceiver?.apply {
            requireActivity().unregisterReceiver(packageBroadcastReceiver)
            packageBroadcastReceiver = null
        } ?: run {
            Log.d("Start", "unregisterBroadcast() was called to a null receiver.")
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        unregisterBroadcast()
    }

    private suspend fun destroyTile(tile: Tile) {
        tile.apply {
            tileType = -1
            tileSize = "small"
            tilePackage = ""
            tileColor = -1
            tileLabel = ""
            id = this.id!! / 2
            mainViewModel.getTileDao().updateTile(this)
        }
    }

    private suspend fun animateTiles(launchApp: Boolean, launchAppPos: Int?, packageName: String?) {
        mAdapter ?: return
        mSpannedLayoutManager ?: return
        if (launchApp && launchAppPos != null && packageName != null) {
            enterToAppAnim(launchAppPos, packageName)
        } else if (!launchApp) {
            exitFromAppAnim()
        }
    }

    private suspend fun enterToAppAnim(position: Int, packageName: String) {
        binding.startTiles.isScrollEnabled = false
        val first = mSpannedLayoutManager!!.firstVisiblePosition
        val last = mSpannedLayoutManager!!.lastVisiblePosition
        val interpolator = AccelerateInterpolator()
        var duration = 175L
        for (i in last downTo first) {
            if (tiles!![i] == tiles!![position]) continue
            val holder = binding.startTiles.findViewHolderForAdapterPosition(i) ?: continue
            if (holder.itemViewType == mAdapter!!.spaceType) continue
            delay(5)
            duration += 10L
            holder.itemView.animate().rotationY(-110f).translationX(-1000f).translationY(-100f)
                .setInterpolator(interpolator).setDuration(duration).start()
        }
        delay(250)
        duration = 150L
        for (i in last downTo first) {
            if (tiles!![i] != tiles!![position]) continue
            val holder = binding.startTiles.findViewHolderForAdapterPosition(i) ?: continue
            holder.itemView.animate().rotationY(-90f).translationX(-1000f).translationY(-100f)
                .setInterpolator(interpolator).setDuration(duration).withEndAction {
                binding.startTiles.isScrollEnabled = true
                startApp(packageName)
            }.start()
        }
    }

    private suspend fun exitFromAppAnim() {
        val first = mSpannedLayoutManager!!.firstVisiblePosition
        val last = mSpannedLayoutManager!!.lastVisiblePosition
        var duration = 300L
        for (i in last downTo first) {
            val holder = binding.startTiles.findViewHolderForAdapterPosition(i) ?: continue
            delay(5)
            duration += 5L
            holder.itemView.animate().rotationY(0f).translationX(0f).translationY(0f)
                .setInterpolator(AccelerateInterpolator()).setDuration(duration).start()
        }
    }

    private fun startApp(packageName: String) {
        isAppOpened = true
        if (activity != null) {
            val intent = when (packageName) {
                "ru.dimon6018.metrolauncher" -> Intent(
                    requireActivity(),
                    SettingsActivity::class.java
                )

                else -> requireActivity().packageManager.getLaunchIntentForPackage(packageName)
                    ?.apply {
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    }
            }
            intent?.let { startActivity(it) }
        }
    }

    inner class NewStartAdapter(val context: Context, var list: MutableList<Tile>) :
        RecyclerView.Adapter<RecyclerView.ViewHolder>(), ItemTouchHelperAdapter {

        val defaultTileType: Int = 0
        val spaceType: Int = -1

        var selectedItem: Tile? = null

        private val accentColor: Int by lazy { Utils.launcherAccentColor(requireActivity().theme) }

        var isEditMode = false
        var isTopLeft = false
        var isTopRight = false
        var isBottomLeft = false
        var isBottomRight = false

        init {
            setHasStableIds(true)
        }

        fun setData(newData: MutableList<Tile>) {
            val diffUtilCallback = DiffUtilCallback(list, newData)
            val diffResult = DiffUtil.calculateDiff(diffUtilCallback, false)
            diffResult.dispatchUpdatesTo(this)
            list = newData
            tiles = newData
        }

        fun enableEditMode() {
            Log.d("EditMode", "enter edit mode")
            (requireActivity() as Main).configureViewPagerScroll(false)
            addCallback()
            isEditMode = true
            for (i in 0..binding.startTiles.childCount) {
                val holder = binding.startTiles.findViewHolderForAdapterPosition(i) ?: continue
                if (holder.itemViewType == -1) continue
                holder.itemView.animate().scaleX(0.9f).scaleY(0.9f).setDuration(300).start()
            }
            startEditModeAnim()
        }

        fun startEditModeAnim() {
            for (i in 0..binding.startTiles.childCount) {
                val holder = binding.startTiles.findViewHolderForAdapterPosition(i) ?: continue
                if (holder.itemViewType == -1) continue
                animateItemEditMode(holder.itemView, i)
            }
        }

        fun disableEditMode() {
            Log.d("EditMode", "exit edit mode")
            removeCallback()
            (requireActivity() as Main).configureViewPagerScroll(true)
            isEditMode = false

            for (i in 0..binding.startTiles.childCount) {
                val holder = binding.startTiles.findViewHolderForAdapterPosition(i) ?: continue
                if (holder.itemViewType == -1) continue
                holder.itemView.clearAnimation()
                holder.itemView.animate().scaleX(1f).scaleY(1f).setDuration(300).start()
                holder.itemView.animate().setDuration(300).translationY(0f).translationX(0f).start()
            }
        }

        // tile animation in edit mode
        fun animateItemEditMode(view: View, position: Int) {
            if (!isEditMode || list[position] == selectedItem) return
            val rad = 5
            val randomX = Random.nextFloat() * 2 * rad - rad
            val randomY = Random.nextFloat() * 2 * rad - rad
            view.animate().setDuration(1000).translationX(randomX).translationY(randomY)
                .setInterpolator(
                    LinearInterpolator()
                ).withEndAction {
                animateItemEditMode(view, position)
            }.start()
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
            val inflater = LayoutInflater.from(parent.context)
            return when (viewType) {
                defaultTileType -> TileViewHolder(TileBinding.inflate(inflater, parent, false))
                spaceType -> SpaceViewHolder(SpaceTileBinding.inflate(inflater, parent, false))
                else -> SpaceViewHolder(SpaceTileBinding.inflate(inflater, parent, false))
            }
        }

        override fun getItemCount(): Int {
            return list.size
        }

        override fun getItemId(position: Int): Long {
            return list[position].id!!
        }

        override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
            when (holder.itemViewType) {
                defaultTileType -> bindDefaultTile(
                    holder as TileViewHolder,
                    list[position],
                    position
                )
            }
        }

        private fun bindDefaultTile(holder: TileViewHolder, item: Tile, position: Int) {
            setTileSize(item, holder.binding.tileLabel)
            setTileIconSize(holder.binding.tileIcon, item.tileSize, context.resources)
            setTileColor(holder, item)
            setTileIcon(holder.binding.tileIcon, item)
            setTileText(holder.binding.tileLabel, item)
            setTileAnimation(holder.itemView, position)
        }

        private fun setTileAnimation(view: View, pos: Int) {
            animateItemEditMode(view, pos)
        }

        private fun setTileText(textView: MaterialTextView, item: Tile) {
            textView.text = item.tileLabel
        }

        private fun setTileIcon(imageView: ImageView, item: Tile) {
            imageView.load(mainViewModel.getIconFromCache(item.tilePackage)) {
                listener(onError = { request: ImageRequest, error: ErrorResult ->
                    lifecycleScope.launch(ioDispatcher) {
                        destroyTile(item)
                    }
                })
            }
        }

        private fun setTileIconSize(imageView: ImageView, tileSize: String, res: Resources) {
            imageView.layoutParams.apply {
                when (tileSize) {
                    "small" -> {
                        val size =
                            if (PREFS.isMoreTilesEnabled) res.getDimensionPixelSize(R.dimen.tile_small_moreTiles_on) else res.getDimensionPixelSize(
                                R.dimen.tile_small_moreTiles_off
                            )
                        width = size
                        height = size
                    }

                    "medium" -> {
                        val size =
                            if (PREFS.isMoreTilesEnabled) res.getDimensionPixelSize(R.dimen.tile_medium_moreTiles_on) else res.getDimensionPixelSize(
                                R.dimen.tile_medium_moreTiles_off
                            )
                        width = size
                        height = size
                    }

                    "big" -> {
                        val size =
                            if (PREFS.isMoreTilesEnabled) res.getDimensionPixelSize(R.dimen.tile_big_moreTiles_on) else res.getDimensionPixelSize(
                                R.dimen.tile_big_moreTiles_off
                            )
                        width = size
                        height = size
                    }

                    else -> {
                        width = res.getDimensionPixelSize(R.dimen.tile_medium_moreTiles_off)
                        height = res.getDimensionPixelSize(R.dimen.tile_medium_moreTiles_off)
                    }
                }
            }
        }

        private fun setTileColor(holder: TileViewHolder, item: Tile) {
            if (!PREFS.isWallpaperEnabled) {
                if (item.tileColor != -1) {
                    holder.binding.container.setBackgroundColor(
                        getTileColorFromPrefs(
                            item.tileColor!!,
                            context
                        )
                    )
                } else {
                    holder.binding.container.setBackgroundColor(accentColorFromPrefs(context))
                }
            }
        }

        private fun setTileSize(item: Tile, mTextView: MaterialTextView) {
            mTextView.apply {
                when (item.tileSize) {
                    "small" -> visibility = View.GONE
                    "medium" -> visibility =
                        if (!PREFS.isMoreTilesEnabled) View.VISIBLE else View.GONE

                    "big" -> visibility = View.VISIBLE
                }
            }
        }

        override fun getItemViewType(position: Int): Int {
            return when (list[position].tileType) {
                -1 -> spaceType
                0 -> defaultTileType
                else -> spaceType
            }
        }

        override fun onItemMove(fromPosition: Int, toPosition: Int) {
            if (!isEditMode) {
                enableEditMode()
                return
            }
            if (fromPosition == toPosition) return
            Log.d("ItemMove", "from pos: $fromPosition")
            Log.d("ItemMove", "to pos: $toPosition")
            list.add(toPosition, list.removeAt(fromPosition))
            notifyItemMoved(fromPosition, toPosition)
        }

        override fun onItemDismiss(position: Int) {
            if (!isEditMode) return
            notifyItemChanged(position)
        }

        override fun onDragAndDropCompleted(viewHolder: RecyclerView.ViewHolder?) {
            if (!isEditMode) return
            lifecycleScope.launch(defaultDispatcher) {
                val newData = ArrayList<Tile>()
                for (i in 0 until list.size) {
                    val item = list[i]
                    item.tilePosition = i
                    newData.add(item)
                }
                mainViewModel.getTileDao().updateAllTiles(newData)
                withContext(mainDispatcher) {
                    startEditModeAnim()
                }
            }
        }

        // A popup window that shows the tile buttons (resize, unpin, customize)
        private fun showTilePopupWindow(holder: TileViewHolder, item: Tile, position: Int) {
            binding.startTiles.isScrollEnabled = false
            holder.itemView.clearAnimation()
            val inflater =
                context.getSystemService(Context.LAYOUT_INFLATER_SERVICE) as LayoutInflater
            val popupView: View = inflater.inflate(
                if (item.tileSize == "small" && PREFS.isMoreTilesEnabled) R.layout.tile_window_small else R.layout.tile_window,
                holder.itemView as ViewGroup,
                false
            )
            var width = holder.itemView.width
            var height = holder.itemView.height
            if (item.tileSize == "small") {
                width += if (PREFS.isMoreTilesEnabled) 75 else 50
                height += 50
            }
            val popupWindow = PopupWindow(popupView, width, height, true)
            val resize = popupView.findViewById<MaterialCardView>(R.id.resize)
            val resizeIcon = popupView.findViewById<ImageView>(R.id.resizeIco)
            val settings = popupView.findViewById<MaterialCardView>(R.id.settings)
            val remove = popupView.findViewById<MaterialCardView>(R.id.remove)
            popupWindow.setOnDismissListener {
                binding.startTiles.isScrollEnabled = true
                selectedItem = null
                animateItemEditMode(holder.itemView, position)
            }
            popupWindow.showAsDropDown(
                holder.itemView,
                if (PREFS.isMoreTilesEnabled && item.tileSize == "small") -width / 5 else 0,
                -height,
                Gravity.CENTER
            )
            resizeIcon.apply {
                when (item.tileSize) {
                    "small" -> {
                        rotation = 45f
                        setImageDrawable(
                            ContextCompat.getDrawable(
                                requireContext(),
                                R.drawable.ic_arrow_right
                            )
                        )
                    }

                    "medium" -> {
                        rotation = 0f
                        setImageDrawable(
                            ContextCompat.getDrawable(
                                requireContext(),
                                R.drawable.ic_arrow_right
                            )
                        )
                    }

                    "big" -> {
                        rotation = 45f
                        setImageDrawable(
                            ContextCompat.getDrawable(
                                requireContext(),
                                R.drawable.ic_arrow_up
                            )
                        )
                    }

                    else -> setImageDrawable(
                        ContextCompat.getDrawable(
                            requireContext(),
                            R.drawable.ic_arrow_right
                        )
                    )
                }
            }
            resize.setOnClickListener {
                lifecycleScope.launch(ioDispatcher) {
                    when (item.tileSize) {
                        "small" -> item.tileSize = "medium"
                        "medium" -> item.tileSize = "big"
                        "big" -> item.tileSize = "small"
                    }
                    mainViewModel.getTileDao().updateTile(item)
                    withContext(mainDispatcher) {
                        notifyItemChanged(position)
                    }
                }
                popupWindow.dismiss()
            }
            remove.setOnClickListener {
                lifecycleScope.launch(ioDispatcher) {
                    destroyTile(item)
                    withContext(mainDispatcher) {
                        notifyItemChanged(position)
                    }
                }
                popupWindow.dismiss()
            }
            settings.setOnClickListener {
                showSettingsBottomSheet(item)
                popupWindow.dismiss()
            }
        }

        // The bottom panel with tile settings, which rises from the bottom
        fun showSettingsBottomSheet(item: Tile) {
            val bottomsheet = BottomSheetDialog(context)
            bottomsheet.setContentView(R.layout.start_tile_settings_bottomsheet)
            bottomsheet.dismissWithAnimation = true
            val bottomSheetInternal =
                bottomsheet.findViewById<View>(com.google.android.material.R.id.design_bottom_sheet)
            BottomSheetBehavior.from<View?>(bottomSheetInternal!!).peekHeight =
                context.resources.getDimensionPixelSize(R.dimen.bottom_sheet_size)
            configureTileBottomSheet(bottomSheetInternal, bottomsheet, item)
            bottomsheet.show()
        }

        // creating the interface of the bottom panel is done here
        private fun configureTileBottomSheet(
            bottomSheetInternal: View,
            bottomsheet: BottomSheetDialog,
            item: Tile
        ) {
            val label = bottomSheetInternal.findViewById<MaterialTextView>(R.id.appLabelSheet)
            val colorSub = bottomSheetInternal.findViewById<MaterialTextView>(R.id.chooseColorSub)
            val removeColor =
                bottomSheetInternal.findViewById<MaterialTextView>(R.id.chooseColorRemove)
            val uninstall = bottomSheetInternal.findViewById<MaterialCardView>(R.id.uninstallApp)
            val uninstallLabel =
                bottomSheetInternal.findViewById<MaterialTextView>(R.id.uninstall_label)
            val changeLabel = bottomSheetInternal.findViewById<MaterialCardView>(R.id.editAppLabel)
            val changeColor = bottomSheetInternal.findViewById<MaterialCardView>(R.id.editTileColor)
            val editor = bottomSheetInternal.findViewById<EditText>(R.id.textEdit)
            val textFiled = bottomSheetInternal.findViewById<TextInputLayout>(R.id.textField)
            val labelLayout = bottomSheetInternal.findViewById<LinearLayout>(R.id.changeLabelLayout)
            val labelChangeBtn =
                bottomSheetInternal.findViewById<MaterialCardView>(R.id.labelChange)
            val editLabelText =
                bottomSheetInternal.findViewById<MaterialTextView>(R.id.editAppLabelText)
            val appInfo = bottomSheetInternal.findViewById<MaterialCardView>(R.id.appInfo)
            val chooseTileColor =
                bottomSheetInternal.findViewById<MaterialTextView>(R.id.choose_tile_color)
            val appInfoLabel =
                bottomSheetInternal.findViewById<MaterialTextView>(R.id.app_info_label)

            (if (PREFS.customLightFontPath != null) customLightFont else customFont)?.let {
                label.typeface = it
                colorSub.typeface = it
                removeColor.typeface = it
                uninstallLabel.typeface = it
                editor.typeface = it
                editLabelText.typeface = it
                chooseTileColor.typeface = it
                appInfoLabel.typeface = it
                textFiled.typeface = it
            }
            editLabelText.setOnClickListener {
                labelLayout.visibility = View.VISIBLE
            }
            val originalLabel = context.packageManager.getApplicationLabel(
                context.packageManager.getApplicationInfo(
                    item.tilePackage,
                    0
                )
            ).toString()
            label.text = item.tileLabel
            editor.setText(item.tileLabel)
            editor.hint = originalLabel
            if (item.tileColor == -1) {
                colorSub.visibility = View.GONE
                removeColor.visibility = View.GONE
            } else {
                colorSub.setTextColor(getTileColorFromPrefs(item.tileColor!!, context))
                colorSub.text = getString(
                    R.string.tileSettings_color_sub,
                    getTileColorName(item.tileColor!!, context)
                )
            }
            changeLabel.setOnClickListener {
                labelLayout.visibility = View.VISIBLE
            }
            labelChangeBtn.setOnClickListener {
                lifecycleScope.launch(ioDispatcher) {
                    item.tileLabel =
                        if (editor.text.toString() == "") originalLabel else editor.text.toString()
                    mainViewModel.getTileDao().updateTile(item)
                }
                bottomsheet.dismiss()
            }
            removeColor.setOnClickListener {
                lifecycleScope.launch(ioDispatcher) {
                    item.tileColor = -1
                    mainViewModel.getTileDao().updateTile(item)
                }
                bottomsheet.dismiss()
            }
            changeColor.setOnClickListener {
                val dialog = AccentDialog()
                dialog.configure(item, this@NewStartAdapter, mainViewModel.getTileDao())
                dialog.show(childFragmentManager, "accentDialog")
                bottomsheet.dismiss()
            }
            uninstall.setOnClickListener {
                startActivity(Intent(Intent.ACTION_DELETE).setData(Uri.parse("package:" + item.tilePackage)))
                bottomsheet.dismiss()
            }
            appInfo.setOnClickListener {
                startActivity(
                    Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).setData(
                        Uri.parse(
                            "package:" + item.tilePackage
                        )
                    )
                )
                bottomsheet.dismiss()
            }
        }

        // Default tile
        @SuppressLint("ClickableViewAccessibility")
        inner class TileViewHolder(val binding: TileBinding) :
            RecyclerView.ViewHolder(binding.root), ItemTouchHelperViewHolder {

            private val gestureDetector: GestureDetector =
                GestureDetector(context, object : SimpleOnGestureListener() {
                    override fun onLongPress(e: MotionEvent) {
                        handleLongClick()
                    }

                    override fun onSingleTapUp(e: MotionEvent): Boolean {
                        handleClick()
                        return true
                    }
                })

            init {
                if (PREFS.isWallpaperEnabled) {
                    itemView.viewTreeObserver.addOnPreDrawListener {
                        if (!isViewPagerScrolling) (itemView.parent as? StartRecyclerView)?.invalidate()
                        true
                    }
                }
                binding.cardContainer.apply {
                    if (PREFS.coloredStroke) strokeColor = accentColor
                }
                binding.container.apply {
                    alpha = PREFS.tilesTransparency
                    setOnTouchListener { view, event ->
                        val x = event.x
                        val y = event.y
                        val left = view.left
                        val top = view.top
                        val right = view.right
                        val bottom = view.bottom

                        isTopLeft =
                            x >= left && x <= left + view.width / 2 && y >= top && y <= top + view.height / 2

                        isTopRight =
                            x >= (left + view.width / 2) && x <= right && y >= top && y <= top + view.height / 2

                        isBottomLeft =
                            x >= left && x <= left + view.width / 2 && y >= top + view.height / 2 && y <= bottom

                        isBottomRight =
                            x >= (left + view.width / 2) && x <= right && y >= top + view.height / 2 && y <= bottom

                        return@setOnTouchListener gestureDetector.onTouchEvent(event)
                    }
                }
                if (PREFS.customFontInstalled) customFont?.let { binding.tileLabel.typeface = it }
            }

            private fun handleClick() {
                val item = list[absoluteAdapterPosition]
                if (isEditMode) {
                    selectedItem = item
                    showTilePopupWindow(this@TileViewHolder, item, absoluteAdapterPosition)
                } else {
                    if (PREFS.isTilesAnimEnabled) {
                        lifecycleScope.launch {
                            animateTiles(true, absoluteAdapterPosition, item.tilePackage)
                        }
                    } else {
                        startApp(item.tilePackage)
                    }
                }
            }

            private fun handleLongClick() {
                if (!isEditMode && !PREFS.isStartBlocked) enableEditMode()
            }

            override fun onItemSelected() {}
            override fun onItemClear() {}
        }

        /**inner class WeatherTileViewHolder(v: View) : RecyclerView.ViewHolder(v), ItemTouchHelperViewHolder {
        val mCardContainer: MaterialCardView = v.findViewById(R.id.cardContainer)
        val mContainer: FrameLayout = v.findViewById(R.id.container)
        val mTextViewAppTitle: TextView = v.findViewById(android.R.id.text1)
        val mTextViewTempValue: TextView = v.findViewById(R.id.weatherTempValue)
        val mTextViewValue: TextView = v.findViewById(R.id.weatherValue)
        val mTextViewCity: TextView = v.findViewById(android.R.id.text2)

        override fun onItemSelected() {}
        override fun onItemClear() {}
        }**/
        inner class SpaceViewHolder(binding: SpaceTileBinding) :
            RecyclerView.ViewHolder(binding.root) {
            init {
                itemView.setOnClickListener {
                    if (isEditMode) disableEditMode()
                }
            }
        }
    }

    class DiffUtilCallback(private val old: List<Tile>, private val new: List<Tile>) :
        DiffUtil.Callback() {
        override fun getOldListSize(): Int {
            return old.size
        }

        override fun getNewListSize(): Int {
            return new.size
        }

        override fun areItemsTheSame(oldItemPosition: Int, newItemPosition: Int): Boolean {
            return old[oldItemPosition] == new[newItemPosition]
        }

        override fun areContentsTheSame(oldItemPosition: Int, newItemPosition: Int): Boolean {
            return old[oldItemPosition].tilePackage == new[newItemPosition].tilePackage
        }
    }

    // Responsible for tile color selection
    class AccentDialog : DialogFragment() {

        private lateinit var item: Tile
        private lateinit var dao: TileDao
        private lateinit var mAdapter: NewStartAdapter

        //available colors
        private val viewIds = arrayOf(
            R.id.choose_color_lime, R.id.choose_color_green, R.id.choose_color_emerald,
            R.id.choose_color_cyan, R.id.choose_color_teal, R.id.choose_color_cobalt,
            R.id.choose_color_indigo, R.id.choose_color_violet, R.id.choose_color_pink,
            R.id.choose_color_magenta, R.id.choose_color_crimson, R.id.choose_color_red,
            R.id.choose_color_orange, R.id.choose_color_amber, R.id.choose_color_yellow,
            R.id.choose_color_brown, R.id.choose_color_olive, R.id.choose_color_steel,
            R.id.choose_color_mauve, R.id.choose_color_taupe
        )

        override fun onCreate(savedInstanceState: Bundle?) {
            super.onCreate(savedInstanceState)
            setStyle(STYLE_NORMAL, R.style.AppTheme_FullScreenDialog)
        }

        fun configure(i: Tile, a: NewStartAdapter, d: TileDao) {
            item = i
            dao = d
            mAdapter = a
        }

        override fun onStart() {
            super.onStart()
            dialog?.apply {
                window!!.setLayout(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT
                )
                setTitle("TILE COLOR")
            }
        }

        override fun onCreateView(
            inflater: LayoutInflater,
            container: ViewGroup?,
            savedInstanceState: Bundle?
        ): View? {
            super.onCreateView(inflater, container, savedInstanceState)
            return inflater.inflate(R.layout.accent_dialog, container, false)
        }

        override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
            super.onViewCreated(view, savedInstanceState)
            val back = view.findViewById<FrameLayout>(R.id.back_accent_menu)
            back.setOnClickListener { dismiss() }
            for (i in 0..<viewIds.size) {
                setOnClick(view.findViewById<ImageView>(viewIds[i]), i)
            }
        }

        private fun setOnClick(colorView: View, value: Int) {
            colorView.setOnClickListener {
                updateTileColor(value)
            }
        }

        private fun updateTileColor(color: Int) {
            lifecycleScope.launch(Dispatchers.IO) {
                item.tileColor = color
                dao.updateTile(item)
                withContext(Dispatchers.Main) {
                    dismiss()
                }
            }
        }

        override fun dismiss() {
            mAdapter.notifyItemChanged(item.tilePosition!!)
            mAdapter.showSettingsBottomSheet(item)
            super.dismiss()
        }
    }
}