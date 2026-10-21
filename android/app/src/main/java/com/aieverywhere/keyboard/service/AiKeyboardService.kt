package com.aieverywhere.keyboard.service

import android.content.BroadcastReceiver
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.res.ColorStateList
import android.graphics.Color
import android.inputmethodservice.InputMethodService
import android.media.AudioManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.speech.tts.TextToSpeech
import android.text.Editable
import android.text.InputType
import android.text.TextWatcher
import android.text.method.ScrollingMovementMethod
import android.view.ActionMode
import android.view.HapticFeedbackConstants
import android.view.KeyEvent
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.ExtractedTextRequest
import android.view.inputmethod.InputMethodManager
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.Spinner
import android.widget.TextView
import android.widget.Toast
import com.aieverywhere.keyboard.R
import com.aieverywhere.keyboard.keyboard.ClipboardPanel
import com.aieverywhere.keyboard.keyboard.ClipboardStore
import com.aieverywhere.keyboard.keyboard.EditTextTarget
import com.aieverywhere.keyboard.keyboard.EmojiPanel
import com.aieverywhere.keyboard.keyboard.FieldKind
import com.aieverywhere.keyboard.keyboard.IcTarget
import com.aieverywhere.keyboard.keyboard.InputTarget
import com.aieverywhere.keyboard.keyboard.KeyType
import com.aieverywhere.keyboard.keyboard.KeyboardActionListener
import com.aieverywhere.keyboard.keyboard.KeyboardTheme
import com.aieverywhere.keyboard.keyboard.KeyboardView
import com.aieverywhere.keyboard.keyboard.ScreenshotWatcher
import com.aieverywhere.keyboard.keyboard.SendResult
import com.aieverywhere.keyboard.keyboard.StickerStore
import com.aieverywhere.keyboard.keyboard.ThemeManager
import com.aieverywhere.keyboard.keyboard.Ui
import com.aieverywhere.keyboard.keyboard.VoiceInput
import com.aieverywhere.keyboard.network.ApiClient
import com.aieverywhere.keyboard.privacy.PiiSanitizer
import com.aieverywhere.keyboard.ui.AiToolbarListener
import com.aieverywhere.keyboard.ui.AiToolbarView
import com.aieverywhere.keyboard.ui.KeyboardHelperActivity
import com.aieverywhere.keyboard.ui.StickerManagerActivity
import com.aieverywhere.keyboard.ui.ThemeActivity
import com.aieverywhere.keyboard.ui.SelectionEditText
import android.widget.Switch

import com.google.firebase.auth.FirebaseAuth
import com.google.mlkit.common.model.DownloadConditions
import com.google.mlkit.nl.translate.TranslateLanguage
import com.google.mlkit.nl.translate.Translation
import com.google.mlkit.nl.translate.TranslatorOptions
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import java.io.File
import java.util.Locale

class AiKeyboardService : InputMethodService(), AiToolbarListener, KeyboardActionListener,
    EmojiPanel.Callbacks, ClipboardPanel.Callbacks, VoiceInput.Callback {

    private enum class Panel { KEYS, EMOJI, CLIPBOARD, TRANSLATE, EDIT, GIF, SETTINGS }

    private val serviceScope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private val handler = Handler(Looper.getMainLooper())
    private val prefs by lazy { ThemeManager.prefs(this) }
    private val nGramStore by lazy { com.aieverywhere.keyboard.keyboard.NGramStore(this) }

    // ---- views
    private lateinit var mainLayout: View
    private lateinit var canvas: FrameLayout
    private lateinit var keyboardHost: FrameLayout
    private lateinit var emojiHost: FrameLayout
    private lateinit var clipboardHost: FrameLayout
    private lateinit var gifHost: LinearLayout
    private lateinit var settingsHost: FrameLayout
    private lateinit var translateView: View
    private lateinit var textEditView: View
    private lateinit var keyboardView: KeyboardView
    private var emojiPanel: EmojiPanel? = null
    private var clipboardPanel: ClipboardPanel? = null
    private var dismissedClipboardId: Long = -1L
    private var currentlyShowingClipboardId: Long = -1L

    private lateinit var previewZone: LinearLayout
    private lateinit var tvPreviewText: SelectionEditText
        private var cardSelectionToolbar: View? = null
    private lateinit var pbLoading: ProgressBar
    private lateinit var ivAiLogo: ImageView
    private lateinit var btnInsert: Button
    private lateinit var btnDiscard: Button
    private lateinit var inlineZone: View
    private lateinit var etInline: EditText
    private lateinit var gifSearchZone: View
    private lateinit var etGifSearchInput: EditText

    private lateinit var theme: KeyboardTheme
    private var themeStamp = ""
    private var viewReady = false

    // ---- state
    private var activeAiResult: CharSequence? = null
    private var pendingCustomTextToProcess = ""
    private var isInternalInputMode = false
    private var lastPastedId: Long = -1L
    private val pillDismissHandler = Handler(Looper.getMainLooper())
    private var fieldKind = FieldKind.TEXT
    private val aiTypingIdleHandler = Handler(Looper.getMainLooper())
    private val aiTypingIdleRunnable = Runnable {
        if (isInternalInputMode && ::previewZone.isInitialized && previewZone.visibility == View.VISIBLE) {
            val suggestionsZone = mainLayout.findViewById<View>(R.id.personal_info_suggestions_zone)
            val svAiActions = mainLayout.findViewById<View>(R.id.sv_ai_actions)
            val svFeatureIcons = mainLayout.findViewById<View>(R.id.sv_feature_icons)
            suggestionsZone?.visibility = View.GONE
            svFeatureIcons?.visibility = View.GONE
            svAiActions?.visibility = View.VISIBLE
        }
    }

    private fun resetAiTypingIdleTimer() {
        aiTypingIdleHandler.removeCallbacks(aiTypingIdleRunnable)
        if (isInternalInputMode && ::previewZone.isInitialized && previewZone.visibility == View.VISIBLE) {
            aiTypingIdleHandler.postDelayed(aiTypingIdleRunnable, 2500L)
        }
    }
    private var lastSpaceAt = 0L
    private var pendingVoiceText: String? = null
    private var inlineTranslated = false
    private var selectMode = false

    private val icTarget by lazy { IcTarget(this) }

    // ---- services
    private val clipboardManager by lazy { getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager }
    private val clipChangedListener = ClipboardManager.OnPrimaryClipChangedListener { capturePrimaryClip() }
    private val audio by lazy { getSystemService(Context.AUDIO_SERVICE) as AudioManager }
    private lateinit var voice: VoiceInput
    private lateinit var screenshots: ScreenshotWatcher
    private var textToSpeech: TextToSpeech? = null
    private var isTtsInitialized = false
    private var pendingSpeech: String? = null

    private val customPromptReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action == "com.aieverywhere.keyboard.CUSTOM_PROMPT_RESULT") {
                val prompt = intent.getStringExtra("prompt")
                if (!prompt.isNullOrBlank()) executeAiAction("CUSTOM_PROMPT", pendingCustomTextToProcess, null, prompt)
            }
        }
    }

    private val helperReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action != KeyboardHelperActivity.ACTION_RESULT) return
            val granted = intent.getBooleanExtra(KeyboardHelperActivity.EXTRA_GRANTED, false)
            when (intent.getStringExtra(KeyboardHelperActivity.EXTRA_MODE)) {
                KeyboardHelperActivity.MODE_MIC -> toast(if (granted) "Microphone allowed. Tap 🎤 to speak." else "Voice typing needs the microphone permission.")
                KeyboardHelperActivity.MODE_MEDIA -> if (granted) {
                    screenshots.start()
                    screenshots.scan()
                    clipboardPanel?.refresh()
                }
                KeyboardHelperActivity.MODE_VOICE -> intent.getStringExtra(KeyboardHelperActivity.EXTRA_TEXT)?.let {
                    // The app regains focus a moment after the voice dialog closes; commit then.
                    pendingVoiceText = it
                    handler.postDelayed({ commitPendingVoice() }, 700)
                }
                KeyboardHelperActivity.MODE_IMAGE_UPLOAD -> {
                    val uriStr = intent.getStringExtra(KeyboardHelperActivity.EXTRA_URI)
                    if (uriStr != null) {
                        val uri = android.net.Uri.parse(uriStr)
                        serviceScope.launch {
                            try {
                                val file = java.io.File(cacheDir, "upload_${System.currentTimeMillis()}.gif")
                                contentResolver.openInputStream(uri)?.use { input ->
                                    file.outputStream().use { output ->
                                        input.copyTo(output)
                                    }
                                }
                                sendImage(file)
                            } catch (e: Exception) {
                                toast("Failed to upload image")
                            }
                        }
                    }
                }
            }
        }
    }

    // ================================================================== lifecycle

    override fun onCreate() {
        super.onCreate()
        registerLocal(customPromptReceiver, "com.aieverywhere.keyboard.CUSTOM_PROMPT_RESULT")
        registerLocal(helperReceiver, KeyboardHelperActivity.ACTION_RESULT)
        voice = VoiceInput(this, this)
        screenshots = ScreenshotWatcher(this) { clipboardPanel?.refresh(); handler.post { updateWordSuggestions() } }
        try {
            clipboardManager.addPrimaryClipChangedListener(clipChangedListener)
        } catch (e: Exception) {
        }
        screenshots.start()
        initTts()
    }

    private fun registerLocal(r: BroadcastReceiver, action: String) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(r, IntentFilter(action), Context.RECEIVER_NOT_EXPORTED)
        } else {
            @Suppress("UnspecifiedRegisterReceiverFlag")
            registerReceiver(r, IntentFilter(action))
        }
    }

    override fun onDestroy() {
        try { clipboardManager.removePrimaryClipChangedListener(clipChangedListener) } catch (e: Exception) {}
        try { unregisterReceiver(customPromptReceiver) } catch (e: Exception) {}
        try { unregisterReceiver(helperReceiver) } catch (e: Exception) {}
        aiTypingIdleHandler.removeCallbacksAndMessages(null)
        voice.destroy()
        screenshots.stop()
        textToSpeech?.stop()
        textToSpeech?.shutdown()
        serviceScope.cancel()
        super.onDestroy()
    }

    override fun onEvaluateFullscreenMode(): Boolean = false
    override fun onEvaluateInputViewShown(): Boolean = true

    override fun onCreateInputView(): View = buildInputView()

    override fun onStartInputView(info: EditorInfo?, restarting: Boolean) {
        super.onStartInputView(info, restarting)
        if (::keyboardView.isInitialized) {
            keyboardView.popupEnabled = prefs.getBoolean(com.aieverywhere.keyboard.keyboard.ThemeManager.KEY_POPUP, true)
        }
        capturePrimaryClip()
        screenshots.scan()
        
        window?.window?.let { w ->
            w.navigationBarColor = theme.background
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                var flags = w.decorView.systemUiVisibility
                if (androidx.core.graphics.ColorUtils.calculateLuminance(theme.background) > 0.5) {
                    flags = flags or android.view.View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR
                } else {
                    flags = flags and android.view.View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR.inv()
                }
                w.decorView.systemUiVisibility = flags
            }
        }

        if (!viewReady || ThemeManager.stamp(this) != themeStamp) setInputView(buildInputView())

        if (!isInternalInputMode) {
            hidePreviewCard()
            inlineZone.visibility = View.GONE
            gifSearchZone.visibility = View.GONE
            showPanel(Panel.KEYS)
        }

        fieldKind = detectFieldKind(info)
        keyboardView.configure(fieldKind)
        keyboardView.setEnterIcon(enterIconFor(info))

        val password = isPassword(info)
        mainLayout.findViewById<View>(R.id.btn_toolbar_toggle)?.visibility = if (password) View.GONE else View.VISIBLE
        if (password) mainLayout.findViewById<View>(R.id.sv_ai_actions)?.visibility = View.GONE

        // Smart Personal Info Suggestions
        val suggestionsZone = mainLayout.findViewById<View>(R.id.personal_info_suggestions_zone)
        val suggestionsContainer = mainLayout.findViewById<LinearLayout>(R.id.ll_personal_info_container)

        if (suggestionsZone != null && suggestionsContainer != null) {
            suggestionsContainer.removeAllViews()
            suggestionsZone.visibility = View.GONE

            if (!password) {
                val hint = info?.hintText?.toString()?.lowercase(Locale.ROOT) ?: ""
                val isEmail = fieldKind == FieldKind.EMAIL || hint.contains("email")
                val isName = hint.contains("name") || hint.contains("first name") || hint.contains("last name")

                val suggestions = mutableListOf<String>()
                val user = FirebaseAuth.getInstance().currentUser

                if (isEmail && user?.email != null) {
                    suggestions.add(user.email!!)
                }
                if (isName && user?.displayName?.isNotEmpty() == true) {
                    suggestions.add(user.displayName!!)
                }

                if (suggestions.isNotEmpty() && !isEmail) {
                    suggestions.forEach { text ->
                        val chip = TextView(this).apply {
                            this.text = text
                            setTextColor(theme.keyText)
                            textSize = 14f
                            maxLines = 1
                            ellipsize = android.text.TextUtils.TruncateAt.END
                            gravity = android.view.Gravity.CENTER
                            includeFontPadding = false
                            background = Ui.rounded(theme.keyBg, Ui.dpf(this@AiKeyboardService, 16f))
                            setPadding(Ui.dp(this@AiKeyboardService, 14f), 0, Ui.dp(this@AiKeyboardService, 14f), 0)
                            layoutParams = LinearLayout.LayoutParams(
                                LinearLayout.LayoutParams.WRAP_CONTENT,
                                Ui.dp(this@AiKeyboardService, 34f)
                            ).apply {
                                setMargins(Ui.dp(this@AiKeyboardService, 4f), 0, Ui.dp(this@AiKeyboardService, 4f), 0)
                            }
                            isClickable = true
                            setOnClickListener {
                                target().commitText(text)
                            }
                        }
                        
                        suggestionsContainer.addView(chip)
                    }
                    suggestionsZone.visibility = View.VISIBLE
                } else {
                    updateWordSuggestions()
                }
            } else {
                updateWordSuggestions()
            }
        } else {
            updateWordSuggestions()
        }

        updateAutoCaps()
        commitPendingVoice()
        screenshots.start()
        screenshots.scan(maxAgeSec = 300, limit = 2)
    }

    override fun onFinishInputView(finishingInput: Boolean) {
        super.onFinishInputView(finishingInput)
        if (voice.isListening) voice.cancel()
        if (::keyboardView.isInitialized) keyboardView.dismissPopups()
        if (fieldKind == FieldKind.EMAIL) {
            try {
                val ic = currentInputConnection
                val full = (ic?.getTextBeforeCursor(100, 0)?.toString() ?: "") + (ic?.getTextAfterCursor(100, 0)?.toString() ?: "")
                emailRegex.findAll(full).lastOrNull()?.let { rememberEmail(it.value) }
            } catch (_: Exception) {}
        }
        if (currentlyShowingClipboardId != -1L) {
            currentlyShowingClipboardId = -1L
        }
    }

    override fun onUpdateSelection(oldSelStart: Int, oldSelEnd: Int, newSelStart: Int, newSelEnd: Int, candidatesStart: Int, candidatesEnd: Int) {
        super.onUpdateSelection(oldSelStart, oldSelEnd, newSelStart, newSelEnd, candidatesStart, candidatesEnd)
        if (newSelStart == newSelEnd) updateAutoCaps()
    }

    // ================================================================== building the view

    private fun buildInputView(): View {
        theme = ThemeManager.resolve(this)
        themeStamp = ThemeManager.stamp(this)
        emojiPanel = null
        clipboardPanel = null
        gifsLoaded = false

        mainLayout = layoutInflater.inflate(R.layout.keyboard_main_layout, null)
        canvas = mainLayout.findViewById(R.id.keyboard_canvas)
        keyboardHost = mainLayout.findViewById(R.id.qwerty_view)
        emojiHost = mainLayout.findViewById(R.id.emoji_view)
        clipboardHost = mainLayout.findViewById(R.id.clipboard_view)
        gifHost = mainLayout.findViewById(R.id.gif_view)
        
        // Dynamically style the search container and placeholders
        gifHost.findViewById<android.widget.LinearLayout>(R.id.ll_gif_search_container)?.background = com.aieverywhere.keyboard.keyboard.Ui.rounded(theme.previewBg, com.aieverywhere.keyboard.keyboard.Ui.dpf(this, 18f))
        gifHost.findViewById<android.widget.TextView>(R.id.et_gif_search)?.setTextColor(theme.keyText)
        gifHost.findViewById<android.widget.TextView>(R.id.et_gif_search)?.setHintTextColor(theme.keyText and 0x88FFFFFF.toInt())
        
        for (i in 1..9) {
            val resId = resources.getIdentifier("gif_$i", "id", packageName)
            gifHost.findViewById<android.widget.ImageView>(resId)?.background = com.aieverywhere.keyboard.keyboard.Ui.rounded(theme.previewBg, com.aieverywhere.keyboard.keyboard.Ui.dpf(this, 6f))
        }
        
        gifHost.findViewById<android.view.View>(R.id.btn_upload_image)?.findViewById<android.widget.ImageView>(R.id.iv_upload_icon)?.let { com.aieverywhere.keyboard.keyboard.Ui.tint(it, theme.icon) }
        gifHost.findViewById<android.view.View>(R.id.btn_upload_image)?.background = com.aieverywhere.keyboard.keyboard.Ui.rounded(theme.previewBg, com.aieverywhere.keyboard.keyboard.Ui.dpf(this, 6f))
        gifHost.findViewById<android.view.View>(R.id.btn_upload_image)?.setOnClickListener {
            KeyboardHelperActivity.launch(this, KeyboardHelperActivity.MODE_IMAGE_UPLOAD)
        }
        gifHost.findViewById<android.view.View>(R.id.btn_gif_abc)?.setOnClickListener {
            showPanel(Panel.KEYS)
        }
        

        translateView = mainLayout.findViewById(R.id.translate_view)
        textEditView = mainLayout.findViewById(R.id.text_edit_view)
        previewZone = mainLayout.findViewById(R.id.preview_card_zone)
        tvPreviewText = mainLayout.findViewById(R.id.tv_preview_text)
        tvPreviewText.movementMethod = android.text.method.ArrowKeyMovementMethod.getInstance()
        pbLoading = mainLayout.findViewById(R.id.pb_loading)
        ivAiLogo = mainLayout.findViewById(R.id.iv_ai_logo)
        btnInsert = mainLayout.findViewById(R.id.btn_insert)
        btnDiscard = mainLayout.findViewById(R.id.btn_discard)
        inlineZone = mainLayout.findViewById(R.id.inline_translate_zone)
        etInline = mainLayout.findViewById(R.id.et_inline_translate_input)
        gifSearchZone = mainLayout.findViewById(R.id.gif_search_zone)
        etGifSearchInput = mainLayout.findViewById(R.id.et_gif_search_input)

        // Keyboard height follows the "key size" setting; every panel uses the same height.
        val rows = if (prefs.getBoolean(ThemeManager.KEY_NUMBER_ROW, false)) 5 else 4
        canvas.layoutParams = canvas.layoutParams.apply {
            height = Ui.dp(this@AiKeyboardService, (rows * (ThemeManager.keyHeightDp(this@AiKeyboardService) + 9) + 8).toFloat())
        }

        keyboardView = KeyboardView(this, theme, this).apply {
            numberRow = prefs.getBoolean(ThemeManager.KEY_NUMBER_ROW, false)
            popupEnabled = prefs.getBoolean(ThemeManager.KEY_POPUP, true)
            configure(FieldKind.TEXT)
        }
        keyboardHost.addView(keyboardView, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
        
        val trailView = com.aieverywhere.keyboard.keyboard.SwipeTrailView(this).apply {
            layoutParams = FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
            isClickable = false
            isFocusable = false
        }
        keyboardHost.addView(trailView)
        keyboardView.trailView = trailView

        setupPreviewCard()
        setupInlineTranslate()
        setupGifSearch()
        setupTranslateView()
        setupTextEditView()
        AiToolbarView(mainLayout, this)
        settingsHost = mainLayout.findViewById(R.id.settings_view)
        layoutInflater.inflate(R.layout.panel_keyboard_settings, settingsHost, true)
        
        settingsHost.findViewById<View>(R.id.btnSettingsBack).setOnClickListener {
            showPanel(Panel.KEYS)
        }
        
        val sp = getSharedPreferences("AIEverywherePrefs", Context.MODE_PRIVATE)
        val swSuggestions = settingsHost.findViewById<Switch>(R.id.switchPanelSuggestions)
        val swSummarise = settingsHost.findViewById<Switch>(R.id.switchPanelSummarise)
        val swAutoCorrect = settingsHost.findViewById<Switch>(R.id.switchPanelAutoCorrection)
        
        swSuggestions?.isChecked = sp.getBoolean("WORD_SUGGESTIONS", true)
        swSuggestions?.setOnCheckedChangeListener { _, isChecked ->
            sp.edit().putBoolean("WORD_SUGGESTIONS", isChecked).apply()
        }
        
        swSummarise?.isChecked = sp.getBoolean("SUMMARISE_REPLY", true)
        swSummarise?.setOnCheckedChangeListener { _, isChecked ->
            sp.edit().putBoolean("SUMMARISE_REPLY", isChecked).apply()
        }
        
        swAutoCorrect?.isChecked = sp.getBoolean("AUTO_CORRECTION", true)
        swAutoCorrect?.setOnCheckedChangeListener { _, isChecked ->
            sp.edit().putBoolean("AUTO_CORRECTION", isChecked).apply()
        }
        
        settingsHost.findViewById<View>(R.id.btnPanelTheme)?.setOnClickListener {
            val intent = Intent(this, com.aieverywhere.keyboard.ui.ThemeActivity::class.java)
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            startActivity(intent)
        }
        
        mainLayout.findViewById<View>(R.id.btn_action_settings)?.setOnClickListener {
            val intent = Intent(this, com.aieverywhere.keyboard.ui.KeyboardSettingsActivity::class.java)
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
            startActivity(intent)
        }
        applyThemeToChrome()
        viewReady = true
        return mainLayout
    }

    private fun applyThemeToChrome() {
        val t = theme
        val root = mainLayout
        root.setBackgroundColor(Color.TRANSPARENT); window.window?.setBackgroundDrawable(android.graphics.drawable.ColorDrawable(Color.TRANSPARENT))
        val kbBody = mainLayout.findViewById<View>(R.id.ll_keyboard_body)
        kbBody?.setBackgroundColor(t.background)
        t.bgImagePath?.let { path ->
            com.aieverywhere.keyboard.keyboard.Ui.photoDrawable(path, t.bgImageDim, t.bgImageFocus)?.let { kbBody?.background = it }
        }
        val logo = setOf(R.id.btn_toolbar_toggle)
        // AI result box + translate bar: styled as separate floating card panels
        val cardBg = if (t.isDark) t.keyBg else 0xFFFFFFFF.toInt()
        val cardStroke = if (t.isDark) 0x40FFFFFF else 0x26000000
        previewZone.background = com.aieverywhere.keyboard.keyboard.Ui.rounded(cardBg, com.aieverywhere.keyboard.keyboard.Ui.dpf(this, 14f), cardStroke, com.aieverywhere.keyboard.keyboard.Ui.dp(this, 1f))
        previewZone.elevation = com.aieverywhere.keyboard.keyboard.Ui.dpf(this, 6f)
        com.aieverywhere.keyboard.keyboard.Ui.applyTheme(previewZone, t, logo) { null }
        btnInsert.setTextColor(t.accent)
        btnDiscard.setTextColor(t.hintText)
        mainLayout.findViewById<ImageButton>(R.id.btn_copy_response)?.imageTintList = ColorStateList.valueOf(t.icon)
        inlineZone.background = com.aieverywhere.keyboard.keyboard.Ui.rounded(cardBg, com.aieverywhere.keyboard.keyboard.Ui.dpf(this, 14f), cardStroke, com.aieverywhere.keyboard.keyboard.Ui.dp(this, 1f))
        inlineZone.elevation = com.aieverywhere.keyboard.keyboard.Ui.dpf(this, 6f)
        com.aieverywhere.keyboard.keyboard.Ui.applyTheme(inlineZone, t) { null }
        mainLayout.findViewById<android.widget.ImageView>(R.id.btn_inline_insert)?.let { com.aieverywhere.keyboard.keyboard.Ui.tint(it, t.accent) }
        mainLayout.findViewById<View>(R.id.inline_translate_underline)?.setBackgroundColor(t.accent)
        val toolbarBg = if (t.isDark) 0xFF24262A.toInt() else 0xFFF0F2F5.toInt()
        val toolbarStroke = if (t.isDark) 0xFF3C3F45.toInt() else 0xFFD0D4DC.toInt()
        cardSelectionToolbar?.background = com.aieverywhere.keyboard.keyboard.Ui.rounded(toolbarBg, com.aieverywhere.keyboard.keyboard.Ui.dpf(this, 8f), toolbarStroke, com.aieverywhere.keyboard.keyboard.Ui.dp(this, 1f))
        val tbTextColor = if (t.isDark) 0xFFFFFFFF.toInt() else 0xFF1F1F1F.toInt()
        val tbDivColor = if (t.isDark) 0xFF3C3F45.toInt() else 0xFFD0D4DC.toInt()
        listOf(R.id.btn_bar_cut, R.id.btn_bar_copy, R.id.btn_bar_paste, R.id.btn_bar_select_all).forEach { id ->
            mainLayout.findViewById<TextView>(id)?.setTextColor(tbTextColor)
        }
        listOf(R.id.div_bar_cut, R.id.div_bar_copy, R.id.div_bar_paste, R.id.div_bar_close).forEach { id ->
            mainLayout.findViewById<View>(id)?.setBackgroundColor(tbDivColor)
        }
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) tvPreviewText.textCursorDrawable?.setTint(t.accent)

        gifSearchZone.background = com.aieverywhere.keyboard.keyboard.Ui.rounded(cardBg, com.aieverywhere.keyboard.keyboard.Ui.dpf(this, 14f), cardStroke, com.aieverywhere.keyboard.keyboard.Ui.dp(this, 1f))
        gifSearchZone.elevation = com.aieverywhere.keyboard.keyboard.Ui.dpf(this, 6f)
        com.aieverywhere.keyboard.keyboard.Ui.applyTheme(gifSearchZone, t) { null }
        mainLayout.findViewById<android.widget.ImageView>(R.id.btn_gif_search_submit)?.let { com.aieverywhere.keyboard.keyboard.Ui.tint(it, t.accent) }
        mainLayout.findViewById<View>(R.id.gif_search_underline)?.setBackgroundColor(t.accent)

        // Toolbar
        mainLayout.findViewById<View>(R.id.kb_toolbar)?.let { bar ->
            com.aieverywhere.keyboard.keyboard.Ui.applyTheme(bar, t, logo) { com.aieverywhere.keyboard.keyboard.Ui.chipBackground(this, t) }
        }
        mainLayout.findViewById<android.widget.ImageButton>(R.id.btn_action_gif)?.let { gifBtn ->
            gifBtn.setImageResource(R.drawable.ic_gif_pixel)
            gifBtn.imageTintList = android.content.res.ColorStateList.valueOf(t.icon)
        }
        mainLayout.findViewById<android.widget.ImageButton>(R.id.btn_action_mic)?.let { micBtn ->
            micBtn.background = Ui.rounded(t.accent, Ui.dpf(this, 100f))
            micBtn.imageTintList = android.content.res.ColorStateList.valueOf(t.accentText)
        }
        mainLayout.findViewById<ImageButton>(R.id.btn_toolbar_toggle)?.let { aiBtn ->
            aiBtn.setBackgroundResource(R.drawable.bg_avatar_circle)
            aiBtn.imageTintList = android.content.res.ColorStateList.valueOf(android.graphics.Color.WHITE)
        }
        
        ivAiLogo.background = null
        ivAiLogo.setPadding(0, 0, 0, 0)
        ivAiLogo.imageTintList = android.content.res.ColorStateList.valueOf(t.icon)

        // Translate + text edit panels
        Ui.applyTheme(translateView, t) { null }
        translateView.findViewById<TextView>(R.id.tv_translate_result)?.setTextColor(t.accent)
        translateView.findViewById<Button>(R.id.btn_do_translate)?.setTextColor(t.accent)
        translateView.findViewById<ImageView>(R.id.btn_insert_translate)?.let { Ui.tint(it, t.accent) }
        val specialIds = setOf(R.id.btn_arrow_up, R.id.btn_arrow_down, R.id.btn_arrow_left, R.id.btn_arrow_right)
        Ui.applyTheme(textEditView, t) { b ->
            when {
                b.id == R.id.btn_edit_select_toggle -> Ui.keyBackground(this, t, t.accent)
                b.id in specialIds -> Ui.keyBackground(this, t, t.specialKeyBg)
                else -> Ui.keyBackground(this, t, t.keyBg)
            }
        }
        textEditView.findViewById<Button>(R.id.btn_edit_select_toggle)?.setTextColor(t.accentText)
        textEditView.findViewById<ImageButton>(R.id.btn_edit_backspace)?.background = Ui.keyBackground(this, t, t.specialKeyBg)
    }

    private fun themedSpinnerAdapter(items: List<String>): ArrayAdapter<String> =
        object : ArrayAdapter<String>(this, android.R.layout.simple_spinner_item, items) {
            override fun getView(position: Int, convertView: View?, parent: ViewGroup): View =
                (super.getView(position, convertView, parent) as TextView).apply {
                    setTextColor(theme.accent)
                    textSize = 14f
                }
        }.apply { setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item) }

    // ================================================================== panels

    private var gifsLoaded = false

    private var currentGifQuery = ""

    private fun loadGifs(query: String = "") {
        val apiKey = "Dgtswp8mKvjfDuO1I3NoPpXcWByyf0Aj" 
        val urlString = if (query.isBlank()) {
            "https://api.giphy.com/v1/gifs/trending?api_key=$apiKey&limit=50"
        } else {
            "https://api.giphy.com/v1/gifs/search?api_key=$apiKey&q=${java.net.URLEncoder.encode(query, "UTF-8")}&limit=50"
        }

        Thread {
            try {
                val url = java.net.URL(urlString)
                val connection = url.openConnection() as java.net.HttpURLConnection
                connection.requestMethod = "GET"
                
                val reader = java.io.BufferedReader(java.io.InputStreamReader(connection.inputStream))
                val response = reader.readText()
                reader.close()

                val jsonObject = org.json.JSONObject(response)
                val dataArray = jsonObject.getJSONArray("data")

                handler.post {
                    if (dataArray.length() == 0) {
                        toast("No GIFs found for this search.")
                    }

                    val gifGrid = gifHost.findViewById<android.widget.GridLayout>(R.id.gif_grid) ?: return@post
                    val uploadBtn = gifGrid.getChildAt(0)
                    gifGrid.removeAllViews()
                    if (uploadBtn != null) {
                        gifGrid.addView(uploadBtn)
                    }

                    for (i in 0 until dataArray.length()) {
                        try {
                            val gifObject = dataArray.getJSONObject(i)
                            val images = gifObject.getJSONObject("images")
                            val gifUrl = images.getJSONObject("fixed_height_downsampled").getString("url")
                            val originalUrl = images.getJSONObject("original").getString("url")
                            
                            val imageView = android.widget.ImageView(this@AiKeyboardService).apply {
                                layoutParams = android.widget.GridLayout.LayoutParams().apply {
                                    width = 0
                                    height = com.aieverywhere.keyboard.keyboard.Ui.dp(this@AiKeyboardService, 90f)
                                    columnSpec = android.widget.GridLayout.spec(android.widget.GridLayout.UNDEFINED, 1f)
                                    val m = com.aieverywhere.keyboard.keyboard.Ui.dp(this@AiKeyboardService, 6f)
                                    setMargins(m, m, m, m)
                                }
                                scaleType = android.widget.ImageView.ScaleType.CENTER_CROP
                            }
                            
                            gifGrid.addView(imageView)
                            
                            com.bumptech.glide.Glide.with(this@AiKeyboardService)
                                .asGif()
                                .load(gifUrl)
                                .into(imageView)
                                
                            imageView.setOnClickListener {
                                toast("Inserting GIF...")
                                serviceScope.launch {
                                    try {
                                        val file = java.io.File(cacheDir, "gif_${System.currentTimeMillis()}.gif")
                                        val inStream = java.net.URL(originalUrl).openStream()
                                        file.outputStream().use { out -> inStream.copyTo(out) }
                                        inStream.close()
                                        sendImage(file)
                                    } catch (e: Exception) {
                                        toast("Failed to insert GIF")
                                    }
                                }
                            }
                        } catch (e: Exception) {
                            // Ignore parsing errors for individual GIFs
                        }
                    }
                }
            } catch (e: Exception) {
                handler.post { toast("GIF Error: ${e.message}") }
            }
        }.start()
    }

    private fun showPanel(p: Panel) {
        if (p != Panel.KEYS) {
            inlineZone.visibility = View.GONE
            gifSearchZone.visibility = View.GONE
        }
        keyboardHost.visibility = if (p == Panel.KEYS) View.VISIBLE else View.GONE
        emojiHost.visibility = if (p == Panel.EMOJI) View.VISIBLE else View.GONE
        clipboardHost.visibility = if (p == Panel.CLIPBOARD) View.VISIBLE else View.GONE
        gifHost.visibility = if (p == Panel.GIF) View.VISIBLE else View.GONE
        settingsHost.visibility = if (p == Panel.SETTINGS) View.VISIBLE else View.GONE
        if (p == Panel.GIF) {
            loadGifs(currentGifQuery)
            gifHost.findViewById<android.widget.TextView>(R.id.et_gif_search)?.apply {
                isFocusable = false
                isClickable = true
                setOnClickListener {
                    showPanel(Panel.KEYS)
                    gifSearchZone.visibility = View.VISIBLE
                    etGifSearchInput.setText(currentGifQuery)
                    etGifSearchInput.setSelection(etGifSearchInput.text.length)
                    etGifSearchInput.requestFocus()
                }
            }
        }
        translateView.visibility = if (p == Panel.TRANSLATE) View.VISIBLE else View.GONE
        textEditView.visibility = if (p == Panel.EDIT) View.VISIBLE else View.GONE
        if (p == Panel.EMOJI) {
            if (emojiPanel == null) {
                emojiPanel = EmojiPanel(this, theme, this).also { emojiHost.addView(it, matchParent()) }
            } else emojiPanel?.refresh()
        }
        if (p == Panel.CLIPBOARD) {
            screenshots.scan()
            if (clipboardPanel == null) {
                clipboardPanel = ClipboardPanel(this, theme, screenshots, this).also { clipboardHost.addView(it, matchParent()) }
            } else clipboardPanel?.refresh()
        }
        if (p == Panel.KEYS) keyboardView.dismissPopups()
    }

    private fun matchParent() = FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
    private fun currentPanelIs(v: View) = v.visibility == View.VISIBLE

    // ================================================================== editor info helpers

    private fun detectFieldKind(info: EditorInfo?): FieldKind {
        val type = info?.inputType ?: return FieldKind.TEXT
        return when (type and InputType.TYPE_MASK_CLASS) {
            InputType.TYPE_CLASS_NUMBER, InputType.TYPE_CLASS_DATETIME -> FieldKind.NUMBER
            InputType.TYPE_CLASS_PHONE -> FieldKind.PHONE
            InputType.TYPE_CLASS_TEXT -> when (type and InputType.TYPE_MASK_VARIATION) {
                InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS, InputType.TYPE_TEXT_VARIATION_WEB_EMAIL_ADDRESS -> FieldKind.EMAIL
                InputType.TYPE_TEXT_VARIATION_URI -> FieldKind.URL
                else -> FieldKind.TEXT
            }
            else -> FieldKind.TEXT
        }
    }

    private fun isPassword(info: EditorInfo?): Boolean {
        val type = info?.inputType ?: return false
        val variation = type and InputType.TYPE_MASK_VARIATION
        return when (type and InputType.TYPE_MASK_CLASS) {
            InputType.TYPE_CLASS_TEXT -> variation == InputType.TYPE_TEXT_VARIATION_PASSWORD ||
                variation == InputType.TYPE_TEXT_VARIATION_WEB_PASSWORD ||
                variation == InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD
            InputType.TYPE_CLASS_NUMBER -> variation == InputType.TYPE_NUMBER_VARIATION_PASSWORD
            else -> false
        }
    }

    private fun enterAction(info: EditorInfo?): Int {
        val opts = info?.imeOptions ?: return EditorInfo.IME_ACTION_NONE
        if (opts and EditorInfo.IME_FLAG_NO_ENTER_ACTION != 0) return EditorInfo.IME_ACTION_NONE
        return opts and EditorInfo.IME_MASK_ACTION
    }

    private fun enterIconFor(info: EditorInfo?): Int = when (enterAction(info)) {
        EditorInfo.IME_ACTION_SEARCH -> R.drawable.ic_kb_search
        EditorInfo.IME_ACTION_SEND -> R.drawable.ic_kb_send
        EditorInfo.IME_ACTION_GO, EditorInfo.IME_ACTION_NEXT -> R.drawable.ic_kb_next
        EditorInfo.IME_ACTION_DONE -> R.drawable.ic_kb_done
        else -> R.drawable.ic_kb_enter
    }

    private fun updateAutoCaps() {
        if (!::keyboardView.isInitialized) return
        if (!prefs.getBoolean(ThemeManager.KEY_AUTO_CAPS, true) || fieldKind != FieldKind.TEXT) return
        if (target() !is IcTarget) return
        val ei = currentInputEditorInfo ?: return
        val caps = currentInputConnection?.getCursorCapsMode(ei.inputType) ?: 0
        keyboardView.setAutoShift(caps != 0)
    }

    /** Keys type into the translate box / AI prompt box when those are open, otherwise into the app. */
    private fun target(): InputTarget = when {
        ::inlineZone.isInitialized && inlineZone.visibility == View.VISIBLE -> EditTextTarget(etInline)
        ::gifSearchZone.isInitialized && gifSearchZone.visibility == View.VISIBLE -> EditTextTarget(etGifSearchInput)
        isInternalInputMode -> EditTextTarget(tvPreviewText)
        else -> icTarget
    }

    // ================================================================== KeyboardActionListener

    override fun onKeyText(text: String) {
        hideCardSelectionToolbar()
        if (isInternalInputMode) {
            aiTypingIdleHandler.removeCallbacks(aiTypingIdleRunnable)
        }
        val t = target()
        if (text == " ") {
            val now = SystemClock.uptimeMillis()
            // Double-tap space -> ". " (GBoard behaviour)
            if (now - lastSpaceAt < 450 && fieldKind == FieldKind.TEXT) {
                val before = t.textBeforeCursor(2)
                if (before.length == 2 && before[1] == ' ' && before[0].isLetterOrDigit()) {
                    t.deleteBackward()
                    t.commitText(". ")
                    lastSpaceAt = 0
                    return
                }
            }
            
            // Learn bigram
            val textBefore = t.textBeforeCursor(30).trimEnd()
            val words = textBefore.split(Regex("\\s+"))
            if (words.size >= 2) {
                val w1 = words[words.size - 2].takeLastWhile { it.isLetter() }
                val w2 = words[words.size - 1].takeLastWhile { it.isLetter() }
                if (w1.isNotEmpty() && w2.isNotEmpty()) {
                    nGramStore.addBigram(w1, w2)
                }
            }
            
            lastSpaceAt = now
        } else {
            lastSpaceAt = 0
        }
        t.commitText(text)
        updateWordSuggestions()
    }

    override fun onKeyDelete(wholeWord: Boolean) {
        hideCardSelectionToolbar()
        if (isInternalInputMode) {
            aiTypingIdleHandler.removeCallbacks(aiTypingIdleRunnable)
        }
        if (wholeWord) target().deleteWordBackward() else target().deleteBackward()
        updateWordSuggestions()
    }

    override fun onKeyEnter() {
        hideCardSelectionToolbar()
        if (isInternalInputMode) {
            aiTypingIdleHandler.removeCallbacks(aiTypingIdleRunnable)
        }
        when {
            inlineZone.visibility == View.VISIBLE -> mainLayout.findViewById<View>(R.id.btn_inline_insert)?.performClick()
            gifSearchZone.visibility == View.VISIBLE -> mainLayout.findViewById<View>(R.id.btn_gif_search_submit)?.performClick()
            isInternalInputMode -> btnInsert.performClick()
            else -> {
                val action = enterAction(currentInputEditorInfo)
                if (action != EditorInfo.IME_ACTION_NONE && action != EditorInfo.IME_ACTION_UNSPECIFIED) {
                    currentInputConnection?.performEditorAction(action)
                } else {
                    sendDownUpKeyEvents(KeyEvent.KEYCODE_ENTER)
                }
            }
        }
    }

    override fun onKeyEmoji() = showPanel(Panel.EMOJI)

    override fun onCursorMove(steps: Int) = target().moveCursor(steps)

    /** Shift with selected text changes its case: lower -> Title -> UPPER -> lower (issue #11). */
    override fun onShiftWithSelection(): Boolean {
        val t = target()
        val sel = t.selectedText()
        if (sel.isNullOrEmpty() || sel.none { it.isLetter() }) return false
        t.replaceSelectionKeepSelected(cycleCase(sel))
        return true
    }

    override fun onSpaceLongPress() {
        try {
            (getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager).showInputMethodPicker()
        } catch (e: Exception) {
        }
    }

    override fun onKeyFeedback(view: View, type: KeyType) {
        if (prefs.getBoolean(ThemeManager.KEY_HAPTIC, true)) {
            view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP, HapticFeedbackConstants.FLAG_IGNORE_VIEW_SETTING)
        }
        if (prefs.getBoolean(ThemeManager.KEY_SOUND, false)) {
            val fx = when (type) {
                KeyType.DELETE -> AudioManager.FX_KEYPRESS_DELETE
                KeyType.SPACE -> AudioManager.FX_KEYPRESS_SPACEBAR
                KeyType.ENTER -> AudioManager.FX_KEYPRESS_RETURN
                else -> AudioManager.FX_KEYPRESS_STANDARD
            }
            audio.playSoundEffect(fx, -1f)
        }
    }

    // ================================================================== Emoji / sticker panel

    override fun onEmoji(emoji: String) = target().commitText(emoji)
    override fun onPanelDelete(wholeWord: Boolean) = onKeyDelete(wholeWord)
    override fun onBackToKeyboard() = showPanel(Panel.KEYS)
    override fun onPanelFeedback(v: View) = onKeyFeedback(v, KeyType.CHAR)

    override fun onSticker(file: File) = sendImage(file)

    override fun onAddSticker() {
        startActivity(Intent(this, StickerManagerActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }

    private fun sendImage(file: File) {
        when (StickerStore.sendImage(this, file)) {
            SendResult.INSERTED -> {}
            SendResult.COPIED -> toast("This app doesn't accept stickers here. Image copied — long-press the text box and Paste.")
            SendResult.FAILED -> toast("Couldn't send this image")
        }
    }

    // ================================================================== Clipboard panel

    override fun onClipText(text: String) {
        target().commitText(text)
        showPanel(Panel.KEYS)
    }

    override fun onClipImage(file: File) = sendImage(file)
    override fun onClipBack() = showPanel(Panel.KEYS)
    override fun onRequestScreenshotPermission() = KeyboardHelperActivity.launch(this, KeyboardHelperActivity.MODE_MEDIA)

    private fun capturePrimaryClip() {
        if (!ClipboardStore.isEnabled(this)) return
        try {
            val clip = clipboardManager.primaryClip ?: return
            val desc = clipboardManager.primaryClipDescription
            for (i in 0 until clip.itemCount) {
                val item = clip.getItemAt(i) ?: continue
                val uri = item.uri
                if (uri != null && !StickerStore.isOwnUri(this, uri)) {
                    // Clipboard image section strictly holds screenshots only (same as GBoard)
                    continue
                }
                item.text?.toString()?.takeIf { it.isNotBlank() }?.let { t ->
                    if (t != prefs.getString("LAST_CAPTURED_CLIP", null)) {
                        prefs.edit().putString("LAST_CAPTURED_CLIP", t).apply()
                        ClipboardStore.addText(this, t)
                        handler.post { updateWordSuggestions() }
                    }
                }
            }
            clipboardPanel?.refresh()
        } catch (e: Exception) {
        }
    }

    // ================================================================== AI toolbar

    override fun onAiActionTriggered(actionType: String, tone: String?) {
        val ic = currentInputConnection
        val selectedText = ic?.getSelectedText(0)?.toString()
        val hostText = if (!selectedText.isNullOrEmpty()) selectedText else ic?.getTextBeforeCursor(500, 0)?.toString() ?: ""
        val internalText = if (isInternalInputMode) tvPreviewText.text?.toString() ?: "" else ""
        val textToProcess = if (isInternalInputMode && internalText.isNotBlank()) {
            internalText
        } else if (hostText.isNotBlank()) {
            hostText
        } else if (isInternalInputMode && pendingCustomTextToProcess.isNotBlank()) {
            pendingCustomTextToProcess
        } else ""

        if (actionType == "CUSTOM_PROMPT") {
            showCustomPromptDialog(textToProcess)
            return
        }
        if (textToProcess.isBlank()) {
            toast("Type or select some text first")
            return
        }
        if (isInternalInputMode) exitInternalMode()
        executeAiAction(actionType, textToProcess, tone, null)
    }

    override fun onEmojiToggle() = showPanel(if (currentPanelIs(emojiHost)) Panel.KEYS else Panel.EMOJI)
    override fun onClipboardToggle() = showPanel(if (currentPanelIs(clipboardHost)) Panel.KEYS else Panel.CLIPBOARD)
    override fun onGifToggle() = showPanel(if (currentPanelIs(gifHost)) Panel.KEYS else Panel.GIF)

    override fun onImageUploadToggle() {
    }

    override fun onToolbarCollapsed() {
        hidePreviewCard()
    }

    // ---------------------------------------------------------------- email suggestions (Gboard-style)
    private val emailRegex = Regex("[A-Za-z0-9._%+\\-]+@[A-Za-z0-9.\\-]+\\.[A-Za-z]{2,}")

    private fun savedEmails(): MutableList<String> =
        (prefs.getString("USED_EMAILS", "") ?: "").split("\n").filter { it.isNotBlank() }.toMutableList()

    private fun rememberEmail(email: String) {
        val list = savedEmails()
        list.removeAll { it.equals(email, true) }
        list.add(0, email)
        prefs.edit().putString("USED_EMAILS", list.take(10).joinToString("\n")).apply()
    }

    /** Previously used emails + emails found in clipboard history + account email, newest first. */
    private fun emailCandidates(typed: String): List<String> {
        val all = LinkedHashSet<String>()
        all.addAll(savedEmails())
        if (ClipboardStore.isEnabled(this)) {
            ClipboardStore.all(this).filter { it.type == "text" }.forEach { c ->
                emailRegex.findAll(c.text ?: "").forEach { all.add(it.value) }
            }
        }
        FirebaseAuth.getInstance().currentUser?.email?.let { all.add(it) }
        val t = typed.lowercase(Locale.ROOT)
        return all.filter { t.isEmpty() || (it.lowercase(Locale.ROOT).startsWith(t) && !it.equals(typed, true)) }.take(3)
    }

    /** Returns true if email chips were shown (caller should stop). */
    private fun showEmailSuggestions(zone: View, container: LinearLayout): Boolean {
        if (fieldKind != FieldKind.EMAIL) return false
        val typed = target().textBeforeCursor(60).takeLastWhile { !it.isWhitespace() }
        val emails = emailCandidates(typed)
        if (emails.isEmpty()) return false
        emails.forEach { email ->
            val chip = TextView(this).apply {
                text = email
                setTextColor(theme.keyText)
                textSize = 14f
                maxLines = 1
                ellipsize = android.text.TextUtils.TruncateAt.END
                gravity = android.view.Gravity.CENTER
                background = Ui.rounded(theme.keyBg, Ui.dpf(this@AiKeyboardService, 16f))
                setPadding(Ui.dp(this@AiKeyboardService, 12f), 0, Ui.dp(this@AiKeyboardService, 12f), 0)
                layoutParams = LinearLayout.LayoutParams(0, Ui.dp(this@AiKeyboardService, 34f), 1f).apply {
                    setMargins(Ui.dp(this@AiKeyboardService, 3f), 0, Ui.dp(this@AiKeyboardService, 3f), 0)
                }
                setOnClickListener {
                    val t = target()
                    if (typed.isNotEmpty()) t.deleteSurroundingText(typed.length, 0)
                    t.commitText(email)
                    rememberEmail(email)
                    zone.visibility = View.GONE
                    mainLayout.findViewById<View>(R.id.sv_feature_icons)?.visibility = View.VISIBLE
                }
            }
            container.addView(chip)
        }
        zone.visibility = View.VISIBLE
        mainLayout.findViewById<View>(R.id.sv_feature_icons)?.visibility = View.GONE
        mainLayout.findViewById<View>(R.id.sv_ai_actions)?.visibility = View.GONE
        return true
    }
    private fun updateWordSuggestions() {
        if (!isInternalInputMode && isPassword(currentInputEditorInfo)) return
        val suggestionsZone = mainLayout.findViewById<View>(R.id.personal_info_suggestions_zone)
        val suggestionsContainer = mainLayout.findViewById<LinearLayout>(R.id.ll_personal_info_container)
        val svFeatureIcons = mainLayout.findViewById<View>(R.id.sv_feature_icons)
        val svAiActions = mainLayout.findViewById<View>(R.id.sv_ai_actions)
        val btnToolbarToggle = mainLayout.findViewById<android.widget.ImageButton>(R.id.btn_toolbar_toggle)
        if (suggestionsZone == null || suggestionsContainer == null) return

        if (isInternalInputMode && tvPreviewText.text.isNullOrEmpty()) {
            aiTypingIdleHandler.removeCallbacks(aiTypingIdleRunnable)
            suggestionsZone.visibility = View.GONE
            svFeatureIcons?.visibility = View.GONE
            svAiActions?.visibility = View.VISIBLE
            val scale = resources.displayMetrics.density
            btnToolbarToggle?.setImageResource(R.drawable.ic_cross_black)
            val pad = (6 * scale + 0.5f).toInt()
            btnToolbarToggle?.setPadding(pad, pad, pad, pad)
            return
        }

        val before = target().textBeforeCursor(30)
        val lastWord = before.takeLastWhile { it.isLetter() }

        suggestionsContainer.removeAllViews()

        suggestionsContainer.gravity = android.view.Gravity.CENTER
        if (showEmailSuggestions(suggestionsZone, suggestionsContainer)) return

        val isPassword = (currentInputEditorInfo?.inputType ?: 0).let {
            (it and InputType.TYPE_TEXT_VARIATION_PASSWORD != 0) ||
            (it and InputType.TYPE_TEXT_VARIATION_WEB_PASSWORD != 0) ||
            (it and InputType.TYPE_NUMBER_VARIATION_PASSWORD != 0)
        }
        if (lastWord.isEmpty() && !isPassword) {
            var showedPaste = false
            try {
                val latest = if (!com.aieverywhere.keyboard.keyboard.ClipboardStore.isEnabled(this)) null else com.aieverywhere.keyboard.keyboard.ClipboardStore.all(this).firstOrNull { !it.isPasted && it.id != lastPastedId }
                val maxAge = if (latest?.type == "image") 300_000L else 60_000L
                if (latest != null && (System.currentTimeMillis() - latest.time) < maxAge && latest.id != dismissedClipboardId) {
                    val chip = android.widget.LinearLayout(this).apply {
                        orientation = android.widget.LinearLayout.HORIZONTAL
                        gravity = android.view.Gravity.CENTER_VERTICAL
                        background = Ui.rounded(theme.keyBg, Ui.dpf(this@AiKeyboardService, 18f))
                        clipToOutline = true
                        isClickable = true
                        isFocusable = true
                        layoutParams = android.widget.LinearLayout.LayoutParams(
                            android.widget.LinearLayout.LayoutParams.WRAP_CONTENT,
                            com.aieverywhere.keyboard.keyboard.Ui.dp(this@AiKeyboardService, 36f)
                        ).also { it.gravity = android.view.Gravity.CENTER }
                        setPadding(com.aieverywhere.keyboard.keyboard.Ui.dp(this@AiKeyboardService, 6f), 0, com.aieverywhere.keyboard.keyboard.Ui.dp(this@AiKeyboardService, 10f), 0)
                    }

                    if (latest.type == "text" && latest.text != null) {
                        val iconView = android.widget.ImageView(this).apply {
                            layoutParams = android.widget.LinearLayout.LayoutParams(
                                com.aieverywhere.keyboard.keyboard.Ui.dp(this@AiKeyboardService, 22f),
                                com.aieverywhere.keyboard.keyboard.Ui.dp(this@AiKeyboardService, 22f)
                            ).apply {
                                marginEnd = com.aieverywhere.keyboard.keyboard.Ui.dp(this@AiKeyboardService, 8f)
                                marginStart = com.aieverywhere.keyboard.keyboard.Ui.dp(this@AiKeyboardService, 4f)
                            }
                            setImageResource(R.drawable.ic_assignment)
                            com.aieverywhere.keyboard.keyboard.Ui.tint(this, theme.icon)
                        }
                        val pasteLabel = android.widget.TextView(this).apply {
                            text = latest.text.replace("\n", " ")
                            setTextColor(theme.keyText)
                            textSize = 13.5f
                            typeface = android.graphics.Typeface.create("sans-serif-medium", android.graphics.Typeface.NORMAL)
                            maxLines = 1
                            ellipsize = android.text.TextUtils.TruncateAt.END
                            maxWidth = com.aieverywhere.keyboard.keyboard.Ui.dp(this@AiKeyboardService, 180f)
                        }
                        chip.addView(iconView)
                        chip.addView(pasteLabel)
                        
                        chip.setOnClickListener {
                            pillDismissHandler.removeCallbacksAndMessages(null)
                            lastPastedId = latest.id
                            com.aieverywhere.keyboard.keyboard.ClipboardStore.markPasted(this@AiKeyboardService, latest.id)
                            target().commitText(latest.text)
                            suggestionsZone.visibility = android.view.View.GONE
                            svFeatureIcons?.visibility = android.view.View.VISIBLE
                            svAiActions?.visibility = android.view.View.GONE
                        }
                    } else if (latest.type == "image" && latest.path != null && latest.isScreenshot) {
                        // Gboard-style exact circle thumbnail
                        val circleSize = com.aieverywhere.keyboard.keyboard.Ui.dp(this@AiKeyboardService, 26f)
                        val thumbnail = android.widget.ImageView(this).apply {
                            layoutParams = android.widget.LinearLayout.LayoutParams(circleSize, circleSize).apply {
                                marginEnd = com.aieverywhere.keyboard.keyboard.Ui.dp(this@AiKeyboardService, 8f)
                                marginStart = com.aieverywhere.keyboard.keyboard.Ui.dp(this@AiKeyboardService, 2f)
                            }
                            scaleType = android.widget.ImageView.ScaleType.CENTER_CROP
                            outlineProvider = object : android.view.ViewOutlineProvider() {
                                override fun getOutline(view: android.view.View, outline: android.graphics.Outline) {
                                    outline.setOval(0, 0, view.width, view.height)
                                }
                            }
                            clipToOutline = true
                            val bm = com.aieverywhere.keyboard.keyboard.Ui.decodeScaled(latest.path, circleSize * 2)
                                ?: android.graphics.BitmapFactory.decodeFile(latest.path)
                            if (bm != null) setImageBitmap(bm)
                        }
                        val pasteLabel = android.widget.TextView(this).apply {
                            text = "Recent screenshot"
                            setTextColor(theme.keyText)
                            textSize = 13.5f
                            typeface = android.graphics.Typeface.create("sans-serif-medium", android.graphics.Typeface.NORMAL)
                            maxLines = 1
                        }
                        chip.addView(thumbnail)
                        chip.addView(pasteLabel)
                        
                        chip.setOnClickListener { 
                            pillDismissHandler.removeCallbacksAndMessages(null)
                            lastPastedId = latest.id
                            com.aieverywhere.keyboard.keyboard.ClipboardStore.markPasted(this@AiKeyboardService, latest.id)
                            sendImage(java.io.File(latest.path))
                            suggestionsZone.visibility = android.view.View.GONE
                            svFeatureIcons?.visibility = android.view.View.VISIBLE
                            svAiActions?.visibility = android.view.View.GONE
                        }
                    }

                    // Gboard-style dismiss button on the chip
                    val dismissBtn = android.widget.ImageView(this).apply {
                        val sz = com.aieverywhere.keyboard.keyboard.Ui.dp(this@AiKeyboardService, 18f)
                        val pad = com.aieverywhere.keyboard.keyboard.Ui.dp(this@AiKeyboardService, 3f)
                        layoutParams = android.widget.LinearLayout.LayoutParams(sz, sz).apply {
                            marginStart = com.aieverywhere.keyboard.keyboard.Ui.dp(this@AiKeyboardService, 8f)
                        }
                        setPadding(pad, pad, pad, pad)
                        setImageResource(R.drawable.ic_clear)
                        com.aieverywhere.keyboard.keyboard.Ui.tint(this, theme.hintText)
                        setOnClickListener {
                            dismissedClipboardId = latest.id
                            pillDismissHandler.removeCallbacksAndMessages(null)
                            suggestionsZone.visibility = android.view.View.GONE
                            svFeatureIcons?.visibility = android.view.View.VISIBLE
                            svAiActions?.visibility = android.view.View.GONE
                        }
                    }
                    chip.addView(dismissBtn)
                    
                    if (chip.childCount > 0) {
                        suggestionsContainer.gravity = android.view.Gravity.CENTER
                        suggestionsContainer.addView(chip)
                        suggestionsZone.visibility = android.view.View.VISIBLE
                        svFeatureIcons?.visibility = android.view.View.GONE
                        svAiActions?.visibility = android.view.View.GONE
                        showedPaste = true
                        currentlyShowingClipboardId = latest.id
                        val maxPillAge = if (latest.type == "image") 300_000L else 45_000L
                        val timeLeft = maxPillAge - (System.currentTimeMillis() - latest.time)
                        if (timeLeft > 0) {
                            pillDismissHandler.removeCallbacksAndMessages(null)
                            pillDismissHandler.postDelayed({
                                suggestionsZone.visibility = android.view.View.GONE
                                svFeatureIcons?.visibility = android.view.View.VISIBLE
                                svAiActions?.visibility = android.view.View.GONE
                            }, timeLeft)
                        }
                    }
                }
            } catch (e: Exception) {}

            var hasBigrams = false
            if (!showedPaste && prefs.getBoolean("WORD_SUGGESTIONS", true)) {
                val wordsBefore = before.trimEnd().split(Regex("\\s+"))
                val previousWord = if (wordsBefore.isNotEmpty()) wordsBefore.last().takeLastWhile { it.isLetter() } else ""
                if (previousWord.isNotEmpty()) {
                    val nextWords = nGramStore.getNextWordSuggestions(previousWord, 3)
                    if (nextWords.isNotEmpty()) {
                        hasBigrams = true
                        nextWords.forEachIndexed { index, text ->
                            val chip = android.widget.TextView(this).apply {
                                this.text = text
                                this.gravity = android.view.Gravity.CENTER
                                this.isAllCaps = false
                                this.setTextColor(theme.keyText)
                                this.textSize = 15f
                                this.maxLines = 1
                                this.ellipsize = android.text.TextUtils.TruncateAt.END
                                
                                val attrs = intArrayOf(android.R.attr.selectableItemBackground)
                                val typedArray = this@AiKeyboardService.obtainStyledAttributes(attrs)
                                val backgroundResource = typedArray.getResourceId(0, 0)
                                typedArray.recycle()
                                this.setBackgroundResource(backgroundResource)
                                
                                val paddingHorizontal = com.aieverywhere.keyboard.keyboard.Ui.dp(this@AiKeyboardService, 8f)
                                this.setPadding(paddingHorizontal, 0, paddingHorizontal, 0)

                                val lp = android.widget.LinearLayout.LayoutParams(0, android.widget.LinearLayout.LayoutParams.MATCH_PARENT, 1f)
                                this.layoutParams = lp

                                setOnClickListener {
                                    target().commitText("$text ")
                                    updateWordSuggestions()
                                }
                            }
                            suggestionsContainer.addView(chip)

                            if (index < nextWords.size - 1) {
                                val divider = android.view.View(this).apply {
                                    setBackgroundColor(theme.divider)
                                    val dp1 = com.aieverywhere.keyboard.keyboard.Ui.dp(this@AiKeyboardService, 1f)
                                    layoutParams = android.widget.LinearLayout.LayoutParams(dp1, android.widget.LinearLayout.LayoutParams.MATCH_PARENT).apply {
                                        val dp4 = com.aieverywhere.keyboard.keyboard.Ui.dp(this@AiKeyboardService, 4f)
                                        setMargins(0, dp4, 0, dp4)
                                    }
                                }
                                suggestionsContainer.addView(divider)
                            }
                        }
                        suggestionsZone.visibility = android.view.View.VISIBLE
                        svFeatureIcons?.visibility = android.view.View.GONE
                        svAiActions?.visibility = android.view.View.GONE
                        if (isInternalInputMode) {
                            resetAiTypingIdleTimer()
                        }
                    }
                }
            }

            if (!showedPaste && !hasBigrams) {
                currentlyShowingClipboardId = -1L
                suggestionsZone.visibility = android.view.View.GONE
                if (isInternalInputMode) {
                    svAiActions?.visibility = android.view.View.VISIBLE
                    svFeatureIcons?.visibility = android.view.View.GONE
                } else {
                    svFeatureIcons?.visibility = android.view.View.VISIBLE
                    svAiActions?.visibility = android.view.View.GONE
                }
            } else if (showedPaste && isInternalInputMode) {
                resetAiTypingIdleTimer()
            }

            if (!isInternalInputMode) {
                val scale = resources.displayMetrics.density
                btnToolbarToggle?.setImageResource(R.drawable.app_logo)
                val pad = (6 * scale + 0.5f).toInt()
                btnToolbarToggle?.setPadding(pad, pad, pad, pad)
            } else {
                val scale = resources.displayMetrics.density
                btnToolbarToggle?.setImageResource(R.drawable.ic_cross_black)
                val pad = (6 * scale + 0.5f).toInt()
                btnToolbarToggle?.setPadding(pad, pad, pad, pad)
            }
            return
        }

        if (!prefs.getBoolean("WORD_SUGGESTIONS", true)) {
            suggestionsZone.visibility = android.view.View.GONE
            svFeatureIcons?.visibility = android.view.View.VISIBLE
            svAiActions?.visibility = android.view.View.GONE
            val scale = resources.displayMetrics.density
            btnToolbarToggle?.setImageResource(R.drawable.app_logo)
            val pad = (6 * scale + 0.5f).toInt()
            btnToolbarToggle?.setPadding(pad, pad, pad, pad)
            return
        }
        
        val suggestions = com.aieverywhere.keyboard.keyboard.SwipeDictionary.words
            .filter { it.startsWith(lastWord, ignoreCase = true) && it.length > lastWord.length }
            .take(3)
            .toMutableList()
            
        val emojiMap = mapOf(
            "happy" to "😀", "smile" to "😊", "sad" to "😢", "love" to "❤️",
            "fire" to "🔥", "laugh" to "😂", "cool" to "😎", "wink" to "😉",
            "cry" to "😭", "kiss" to "😘", "angry" to "😠", "thumbs up" to "👍",
            "okay" to "👌", "pray" to "🙏", "party" to "🎉", "star" to "⭐"
        )
        val wordLower = lastWord.lowercase()
        if (emojiMap.containsKey(wordLower)) {
            suggestions.add(0, emojiMap[wordLower]!!)
        }
        if (suggestions.size > 3) suggestions.removeLast()

        // If no suggestions found, fallback to original word
        val finalSuggestions = if (suggestions.isEmpty()) {
            listOf(lastWord)
        } else {
            suggestions
        }

        finalSuggestions.forEachIndexed { index, text ->
            val chip = android.widget.TextView(this).apply {
                this.text = text
                this.gravity = android.view.Gravity.CENTER
                this.isAllCaps = false
                this.setTextColor(theme.keyText) // Use keyboard theme color!
                this.textSize = 15f
                this.maxLines = 1
                this.ellipsize = android.text.TextUtils.TruncateAt.END
                
                val attrs = intArrayOf(android.R.attr.selectableItemBackground)
                val typedArray = this@AiKeyboardService.obtainStyledAttributes(attrs)
                val backgroundResource = typedArray.getResourceId(0, 0)
                typedArray.recycle()
                this.setBackgroundResource(backgroundResource)
                
                val paddingHorizontal = Ui.dp(this@AiKeyboardService, 8f)
                this.setPadding(paddingHorizontal, 0, paddingHorizontal, 0)

                val lp = LinearLayout.LayoutParams(
                    0, // 0dp width with weight=1 distributes them equally
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    1f
                )
                this.layoutParams = lp

                setOnClickListener {
                    val t = target()
                    t.deleteSurroundingText(lastWord.length, 0)
                    t.commitText("$text ")
                    updateWordSuggestions()
                }
            }
            suggestionsContainer.addView(chip)

            if (index < finalSuggestions.size - 1) {
                val divider = android.view.View(this).apply {
                    setBackgroundColor(theme.divider)
                    val dp1 = Ui.dp(this@AiKeyboardService, 1f)
                    val dp4 = Ui.dp(this@AiKeyboardService, 6f)
                    layoutParams = LinearLayout.LayoutParams(dp1, LinearLayout.LayoutParams.MATCH_PARENT).apply {
                        setMargins(0, dp4, 0, dp4)
                    }
                }
                suggestionsContainer.addView(divider)
            }
        }
        suggestionsZone.visibility = View.VISIBLE
        mainLayout.findViewById<View>(R.id.sv_feature_icons)?.visibility = View.GONE
        mainLayout.findViewById<View>(R.id.sv_ai_actions)?.visibility = View.GONE
        if (isInternalInputMode) {
            resetAiTypingIdleTimer()
            val scale = resources.displayMetrics.density
            btnToolbarToggle?.setImageResource(R.drawable.ic_cross_black)
            val pad = (6 * scale + 0.5f).toInt()
            btnToolbarToggle?.setPadding(pad, pad, pad, pad)
        } else if (btnToolbarToggle != null) {
            val scale = resources.displayMetrics.density
            btnToolbarToggle.setImageResource(R.drawable.app_logo)
            val pad = (6 * scale + 0.5f).toInt()
            btnToolbarToggle.setPadding(pad, pad, pad, pad)
        }
    }


    override fun onLanguageKey() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            switchToNextInputMethod(false)
        } else {
            val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as android.view.inputmethod.InputMethodManager
            imm.showInputMethodPicker()
        }
    }
    
    override fun onTranslateToggle() {
        if (inlineZone.visibility == View.VISIBLE) {
            inlineZone.visibility = View.GONE
            return
        }
        showPanel(Panel.KEYS)
        inlineZone.visibility = View.VISIBLE
        inlineTranslated = false
        val selected = currentInputConnection?.getSelectedText(0)?.toString()
        etInline.setText(selected ?: "")
        etInline.setSelection(etInline.text.length)
        etInline.requestFocus()
    }

    // ================================================================== voice typing (issue #5)

    override fun onVoiceTypingToggle() {
        if (voice.isListening) {
            voice.stop()
            return
        }
        if (!voice.hasPermission()) {
            toast("Allow the microphone to use voice typing")
            KeyboardHelperActivity.launch(this, KeyboardHelperActivity.MODE_MIC)
            return
        }
        if (!voice.isAvailable()) {
            // No on-device recognition service: use the system voice dialog instead.
            KeyboardHelperActivity.launch(this, KeyboardHelperActivity.MODE_VOICE)
            return
        }
        voice.start()
    }

    override fun onVoiceListening(listening: Boolean) {
        val mic = mainLayout.findViewById<ImageButton>(R.id.btn_action_mic) ?: return
        val status = mainLayout.findViewById<TextView>(R.id.tv_voice_status)
        val icons = mainLayout.findViewById<View>(R.id.sv_feature_icons)
        val ai = mainLayout.findViewById<View>(R.id.sv_ai_actions)
        if (listening) {
            mic.imageTintList = ColorStateList.valueOf(Color.parseColor("#E53935"))
            status?.text = "Listening… speak now"
            status?.visibility = View.VISIBLE
            icons?.visibility = View.GONE
            ai?.visibility = View.GONE
        } else {
            mic.imageTintList = ColorStateList.valueOf(theme.icon)
            status?.visibility = View.GONE
            icons?.visibility = View.VISIBLE
        }
    }

    override fun onVoicePartial(text: String) {
        mainLayout.findViewById<TextView>(R.id.tv_voice_status)?.text = text
    }

    override fun onVoiceResult(text: String) = insertSpoken(text)

    override fun onVoiceError(message: String, needsPermission: Boolean) {
        toast(message)
        if (needsPermission) KeyboardHelperActivity.launch(this, KeyboardHelperActivity.MODE_MIC)
    }

    private fun insertSpoken(text: String) {
        val t = target()
        val before = t.textBeforeCursor(1)
        val prefix = if (before.isNotEmpty() && !before[0].isWhitespace()) " " else ""
        t.commitText(prefix + text)
    }

    private fun commitPendingVoice() {
        val text = pendingVoiceText ?: return
        if (currentInputConnection == null) return
        pendingVoiceText = null
        insertSpoken(text)
    }

    // ================================================================== text-to-speech (speaker button)

    private fun initTts() {
        isTtsInitialized = false
        textToSpeech = TextToSpeech(applicationContext) { status ->
            if (status == TextToSpeech.SUCCESS) {
                isTtsInitialized = true
                pendingSpeech?.let { pendingSpeech = null; speak(it) }
            } else {
                textToSpeech = null
                handler.post { toast("Text-to-speech engine not available. Install/enable 'Speech Services by Google'.") }
            }
        }
    }

    private fun speak(text: String) {
        val tts = textToSpeech
        if (tts == null || !isTtsInitialized) {
            pendingSpeech = text
            if (tts == null) initTts()
            return
        }
        if (tts.isSpeaking) {
            tts.stop()
            return
        }
        val locale = if (text.any { it in '\u0900'..'\u097F' }) Locale("hi", "IN") else Locale.getDefault()
        if (tts.setLanguage(locale) < 0) tts.setLanguage(Locale.US)
        tts.speak(text, TextToSpeech.QUEUE_FLUSH, null, "ai_kb_speech")
    }

    // ================================================================== AI preview box

    private fun copyPreviewSelectionOrAll() {
        val textToCopy = if (tvPreviewText.hasSelection()) {
            val s = minOf(tvPreviewText.selectionStart, tvPreviewText.selectionEnd)
            val e = maxOf(tvPreviewText.selectionStart, tvPreviewText.selectionEnd)
            if (s in 0..e && e <= (tvPreviewText.text?.length ?: 0)) {
                tvPreviewText.text?.subSequence(s, e)?.toString()
            } else {
                tvPreviewText.text?.toString()
            }
        } else {
            tvPreviewText.text?.toString()
        }

        if (!textToCopy.isNullOrBlank()) {
            clipboardManager.setPrimaryClip(ClipData.newPlainText("AI text", textToCopy))
            toast("Copied to clipboard")
        }
    }

    private fun hasClipboard(): Boolean {
        val clip = clipboardManager.primaryClip ?: return false
        return clip.itemCount > 0 && !clip.getItemAt(0)?.coerceToText(this).isNullOrEmpty()
    }

    private fun hideCardSelectionToolbar() {
        cardSelectionToolbar?.visibility = View.GONE
    }

    private fun updateCardSelectionToolbar() {
        val toolbar = cardSelectionToolbar ?: return
        val hasSel = tvPreviewText.hasSelection()
        val fullLen = tvPreviewText.text?.length ?: 0
        val selStart = minOf(tvPreviewText.selectionStart, tvPreviewText.selectionEnd)
        val selEnd = maxOf(tvPreviewText.selectionStart, tvPreviewText.selectionEnd)
        val allSelected = hasSel && selStart == 0 && selEnd == fullLen
        val hasClip = hasClipboard()

        val canCut = hasSel && isInternalInputMode && fullLen > 0
        val canCopy = hasSel || fullLen > 0
        val canPaste = isInternalInputMode && hasClip
        val canSelectAll = fullLen > 0

        val btnCut = mainLayout.findViewById<View>(R.id.btn_bar_cut) ?: return
        val divCut = mainLayout.findViewById<View>(R.id.div_bar_cut) ?: return
        val btnCopy = mainLayout.findViewById<View>(R.id.btn_bar_copy) ?: return
        val divCopy = mainLayout.findViewById<View>(R.id.div_bar_copy) ?: return
        val btnPaste = mainLayout.findViewById<View>(R.id.btn_bar_paste) ?: return
        val divPaste = mainLayout.findViewById<View>(R.id.div_bar_paste) ?: return
        val btnSelectAll = mainLayout.findViewById<View>(R.id.btn_bar_select_all) ?: return
        val divClose = mainLayout.findViewById<View>(R.id.div_bar_close) ?: return
        val btnClose = mainLayout.findViewById<View>(R.id.btn_bar_close) ?: return

        btnCut.visibility = if (canCut) View.VISIBLE else View.GONE
        btnCopy.visibility = if (canCopy) View.VISIBLE else View.GONE
        btnPaste.visibility = if (canPaste) View.VISIBLE else View.GONE
        btnSelectAll.visibility = if (canSelectAll) View.VISIBLE else View.GONE

        divCut.visibility = if (canCut && (canCopy || canPaste || canSelectAll)) View.VISIBLE else View.GONE
        divCopy.visibility = if (canCopy && (canPaste || canSelectAll)) View.VISIBLE else View.GONE
        divPaste.visibility = if (canPaste && canSelectAll) View.VISIBLE else View.GONE
        
        val anyAction = canCut || canCopy || canPaste || canSelectAll
        divClose.visibility = if (anyAction) View.VISIBLE else View.GONE
        btnClose.visibility = if (anyAction) View.VISIBLE else View.GONE
        toolbar.visibility = if (anyAction) View.VISIBLE else View.GONE
    }

    private fun setupPreviewCard() {
        val btnCopyResponse = mainLayout.findViewById<View>(R.id.btn_copy_response)
        btnCopyResponse?.setOnClickListener { copyPreviewSelectionOrAll() }

        cardSelectionToolbar = mainLayout.findViewById(R.id.card_selection_toolbar)

        mainLayout.findViewById<View>(R.id.btn_bar_cut)?.setOnClickListener {
            val s = minOf(tvPreviewText.selectionStart, tvPreviewText.selectionEnd)
            val e = maxOf(tvPreviewText.selectionStart, tvPreviewText.selectionEnd)
            val text = tvPreviewText.text
            if (text != null && s in 0..e && e <= text.length && s != e) {
                val snippet = text.subSequence(s, e).toString()
                clipboardManager.setPrimaryClip(ClipData.newPlainText("AI text", snippet))
                ClipboardStore.addText(this, snippet)
                text.delete(s, e)
                tvPreviewText.setSelection(s.coerceAtMost(text.length))
                toast("Cut")
                updateWordSuggestions()
            }
            hideCardSelectionToolbar()
        }

        mainLayout.findViewById<View>(R.id.btn_bar_copy)?.setOnClickListener {
            val s = minOf(tvPreviewText.selectionStart, tvPreviewText.selectionEnd)
            val e = maxOf(tvPreviewText.selectionStart, tvPreviewText.selectionEnd)
            val text = tvPreviewText.text?.toString() ?: ""
            val snippet = if (tvPreviewText.hasSelection() && s in 0..e && e <= text.length && s != e) {
                text.substring(s, e)
            } else {
                text
            }
            if (snippet.isNotEmpty()) {
                clipboardManager.setPrimaryClip(ClipData.newPlainText("AI text", snippet))
                ClipboardStore.addText(this, snippet)
                toast("Copied")
                val cur = tvPreviewText.selectionEnd.coerceAtLeast(0)
                tvPreviewText.setSelection(cur.coerceAtMost(tvPreviewText.text?.length ?: 0))
            }
            hideCardSelectionToolbar()
        }

        mainLayout.findViewById<View>(R.id.btn_bar_paste)?.setOnClickListener {
            val clip = clipboardManager.primaryClip?.let { cp ->
                if (cp.itemCount > 0) cp.getItemAt(0)?.coerceToText(this@AiKeyboardService)?.toString() else null
            }
            if (!clip.isNullOrEmpty()) {
                if (isInternalInputMode) {
                    val s = minOf(tvPreviewText.selectionStart, tvPreviewText.selectionEnd)
                    val e = maxOf(tvPreviewText.selectionStart, tvPreviewText.selectionEnd)
                    val txt = tvPreviewText.text
                    if (txt != null && s in 0..e && e <= txt.length) {
                        txt.replace(s, e, clip)
                        tvPreviewText.setSelection((s + clip.length).coerceAtMost(txt.length))
                    } else {
                        tvPreviewText.append(clip)
                    }
                    updateWordSuggestions()
                } else {
                    target().commitText(clip)
                }
            } else {
                toast("Clipboard is empty")
            }
            hideCardSelectionToolbar()
        }

        mainLayout.findViewById<View>(R.id.btn_bar_select_all)?.setOnClickListener {
            tvPreviewText.requestFocus()
            tvPreviewText.selectAll()
            updateCardSelectionToolbar()
        }

        mainLayout.findViewById<View>(R.id.btn_bar_close)?.setOnClickListener {
            hideCardSelectionToolbar()
        }

        tvPreviewText.isFocusable = true
        tvPreviewText.isFocusableInTouchMode = true
        tvPreviewText.isClickable = true
        tvPreviewText.isLongClickable = true
        tvPreviewText.showSoftInputOnFocus = false

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            tvPreviewText.customSelectionActionModeCallback = object : ActionMode.Callback2() {
                override fun onCreateActionMode(mode: ActionMode?, menu: Menu?): Boolean {
                    menu?.clear()
                    return true
                }
                override fun onPrepareActionMode(mode: ActionMode?, menu: Menu?): Boolean {
                    menu?.clear()
                    return false
                }
                override fun onActionItemClicked(mode: ActionMode?, item: MenuItem?): Boolean = false
                override fun onDestroyActionMode(mode: ActionMode?) {
                    if (!tvPreviewText.hasSelection()) {
                        hideCardSelectionToolbar()
                    }
                }
                override fun onGetContentRect(mode: ActionMode?, view: View?, outRect: android.graphics.Rect?) {
                    outRect?.set(-10000, -10000, -10000, -10000)
                }
            }
            tvPreviewText.customInsertionActionModeCallback = object : ActionMode.Callback {
                override fun onCreateActionMode(mode: ActionMode?, menu: Menu?): Boolean {
                    menu?.clear()
                    return false
                }
                override fun onPrepareActionMode(mode: ActionMode?, menu: Menu?): Boolean = false
                override fun onActionItemClicked(mode: ActionMode?, item: MenuItem?): Boolean = false
                override fun onDestroyActionMode(mode: ActionMode?) {}
            }
        } else {
            val legacyCallback = object : ActionMode.Callback {
                override fun onCreateActionMode(mode: ActionMode?, menu: Menu?): Boolean {
                    menu?.clear()
                    return true
                }
                override fun onPrepareActionMode(mode: ActionMode?, menu: Menu?): Boolean {
                    menu?.clear()
                    return false
                }
                override fun onActionItemClicked(mode: ActionMode?, item: MenuItem?): Boolean = false
                override fun onDestroyActionMode(mode: ActionMode?) {
                    if (!tvPreviewText.hasSelection()) {
                        hideCardSelectionToolbar()
                    }
                }
            }
            tvPreviewText.customSelectionActionModeCallback = legacyCallback
        }

        tvPreviewText.onSelectionChangeListener = { hasSelection, start, end ->
            if (hasSelection) {
                tvPreviewText.post { updateCardSelectionToolbar() }
            } else {
                hideCardSelectionToolbar()
            }
        }

        tvPreviewText.setOnLongClickListener {
            if (tvPreviewText.text.isNullOrEmpty()) {
                if (hasClipboard()) {
                    updateCardSelectionToolbar()
                    true
                } else false
            } else {
                false
            }
        }

        tvPreviewText.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                hideCardSelectionToolbar()
                if (isInternalInputMode) {
                    aiTypingIdleHandler.removeCallbacks(aiTypingIdleRunnable)
                }
            }
            override fun afterTextChanged(s: Editable?) {
                if (isInternalInputMode) {
                    updateWordSuggestions()
                }
            }
        })

                tvPreviewText.setOnClickListener {
            hideCardSelectionToolbar()
            if (isInternalInputMode) {
                tvPreviewText.isFocusable = true
                tvPreviewText.isFocusableInTouchMode = true
                tvPreviewText.isCursorVisible = true
                tvPreviewText.requestFocus()
                aiTypingIdleHandler.removeCallbacks(aiTypingIdleRunnable)
                updateWordSuggestions()
            }
        }
        btnInsert.setOnClickListener {
            hideCardSelectionToolbar()
            if (isInternalInputMode) {
                val prompt = tvPreviewText.text?.toString().orEmpty()
                if (prompt.isNotBlank()) {
                    exitInternalMode()
                    executeAiAction("CUSTOM_PROMPT", pendingCustomTextToProcess, null, prompt)
                }
            } else {
                activeAiResult?.let { text ->
                    val ic = currentInputConnection
                    // Replace the text the AI worked on when it was the text before the cursor.
                    if (ic != null && ic.getSelectedText(0).isNullOrEmpty() && pendingReplaceLength > 0) {
                        ic.deleteSurroundingText(pendingReplaceLength, 0)
                    }
                    val textToInsert = if (tvPreviewText.hasSelection()) {
                        val s = minOf(tvPreviewText.selectionStart, tvPreviewText.selectionEnd)
                        val e = maxOf(tvPreviewText.selectionStart, tvPreviewText.selectionEnd)
                        if (s in 0..e && e <= (tvPreviewText.text?.length ?: 0)) {
                            tvPreviewText.text?.subSequence(s, e)?.toString() ?: text.toString()
                        } else text.toString()
                    } else text.toString()
                    ic?.commitText(textToInsert, 1)
                }
                hidePreviewCard()
            }
        }
        btnDiscard.setOnClickListener {
            hideCardSelectionToolbar()
            if (isInternalInputMode) exitInternalMode()
            hidePreviewCard()
        }
    }

    private var pendingReplaceLength = 0

    private fun exitInternalMode() {
        hideCardSelectionToolbar()
        aiTypingIdleHandler.removeCallbacks(aiTypingIdleRunnable)
        isInternalInputMode = false
        btnInsert.text = "Insert"
        btnDiscard.text = "Discard"
        tvPreviewText.setTextColor(theme.keyText)
        updateWordSuggestions()
    }

    private fun hidePreviewCard() {
        if (!::previewZone.isInitialized) return
        hideCardSelectionToolbar()
        aiTypingIdleHandler.removeCallbacks(aiTypingIdleRunnable)
        stopAiThinkingDotsAnimation()
        previewZone.visibility = View.GONE
        activeAiResult = null
        pendingReplaceLength = 0
        mainLayout.findViewById<View>(R.id.btn_copy_response)?.visibility = View.GONE
        if (isInternalInputMode) exitInternalMode()
        updateWordSuggestions()
    }

    private fun showCustomPromptDialog(textToProcess: String) {
        hideCardSelectionToolbar()
        pendingCustomTextToProcess = textToProcess
        isInternalInputMode = true
        inlineZone.visibility = View.GONE
        showPanel(Panel.KEYS)
        stopAiThinkingDotsAnimation()
        previewZone.visibility = View.VISIBLE
        tvPreviewText.setText("", TextView.BufferType.EDITABLE)
        tvPreviewText.hint = "Ask AI anything about your text…"
        tvPreviewText.setTextColor(theme.keyText)
        tvPreviewText.setHintTextColor(theme.hintText)
        tvPreviewText.isFocusable = true
        tvPreviewText.isFocusableInTouchMode = true
        tvPreviewText.isCursorVisible = true
        tvPreviewText.showSoftInputOnFocus = false
        tvPreviewText.isLongClickable = true
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            tvPreviewText.textCursorDrawable?.setTint(theme.accent)
        }
        tvPreviewText.requestFocus()
        tvPreviewText.post {
            tvPreviewText.requestFocus()
            tvPreviewText.isCursorVisible = true
            tvPreviewText.setSelection(tvPreviewText.text?.length ?: 0)
        }
        btnInsert.text = "Send"
        btnInsert.isEnabled = true
        btnDiscard.text = "Cancel"
        mainLayout.findViewById<View>(R.id.btn_copy_response)?.visibility = View.GONE

        aiTypingIdleHandler.removeCallbacks(aiTypingIdleRunnable)
        mainLayout.findViewById<View>(R.id.personal_info_suggestions_zone)?.visibility = View.GONE
        mainLayout.findViewById<View>(R.id.sv_feature_icons)?.visibility = View.GONE
        mainLayout.findViewById<View>(R.id.sv_ai_actions)?.visibility = View.VISIBLE
        mainLayout.findViewById<android.widget.ImageButton>(R.id.btn_toolbar_toggle)?.let { btn ->
            val scale = resources.displayMetrics.density
            btn.setImageResource(R.drawable.ic_cross_black)
            val pad = (6 * scale + 0.5f).toInt()
            btn.setPadding(pad, pad, pad, pad)
        }
        updateWordSuggestions()
    }

    private var aiThinkingAnimators = mutableListOf<android.animation.AnimatorSet>()

    private fun startAiThinkingDotsAnimation() {
        stopAiThinkingDotsAnimation()
        val dotsContainer = mainLayout.findViewById<View>(R.id.ll_ai_thinking_dots) ?: return
        val d1 = mainLayout.findViewById<View>(R.id.dot_1) ?: return
        val d2 = mainLayout.findViewById<View>(R.id.dot_2) ?: return
        val d3 = mainLayout.findViewById<View>(R.id.dot_3) ?: return

        if (::ivAiLogo.isInitialized) ivAiLogo.visibility = View.GONE
        if (::pbLoading.isInitialized) pbLoading.visibility = View.GONE
        dotsContainer.visibility = View.VISIBLE

        val dotColor = if (theme.isDark) android.graphics.Color.parseColor("#AB47BC") else android.graphics.Color.parseColor("#7B1FA2")
        val dotBg1 = android.graphics.drawable.GradientDrawable().apply { shape = android.graphics.drawable.GradientDrawable.OVAL; setColor(dotColor) }
        val dotBg2 = android.graphics.drawable.GradientDrawable().apply { shape = android.graphics.drawable.GradientDrawable.OVAL; setColor(dotColor) }
        val dotBg3 = android.graphics.drawable.GradientDrawable().apply { shape = android.graphics.drawable.GradientDrawable.OVAL; setColor(dotColor) }
        d1.background = dotBg1
        d2.background = dotBg2
        d3.background = dotBg3

        fun createBounce(v: View, delay: Long): android.animation.AnimatorSet {
            val animY = android.animation.ObjectAnimator.ofFloat(v, View.TRANSLATION_Y, 0f, -7f, 0f).apply {
                duration = 450
                repeatCount = android.animation.ValueAnimator.INFINITE
                repeatMode = android.animation.ValueAnimator.RESTART
                startDelay = delay
                interpolator = android.view.animation.AccelerateDecelerateInterpolator()
            }
            return android.animation.AnimatorSet().apply {
                play(animY)
                start()
            }
        }

        aiThinkingAnimators.clear()
        aiThinkingAnimators.add(createBounce(d1, 0L))
        aiThinkingAnimators.add(createBounce(d2, 140L))
        aiThinkingAnimators.add(createBounce(d3, 280L))
    }

    private fun stopAiThinkingDotsAnimation() {
        aiThinkingAnimators.forEach { it.cancel() }
        aiThinkingAnimators.clear()
        val dotsContainer = mainLayout.findViewById<View>(R.id.ll_ai_thinking_dots)
        val d1 = mainLayout.findViewById<View>(R.id.dot_1)
        val d2 = mainLayout.findViewById<View>(R.id.dot_2)
        val d3 = mainLayout.findViewById<View>(R.id.dot_3)

        d1?.translationY = 0f
        d2?.translationY = 0f
        d3?.translationY = 0f
        dotsContainer?.visibility = View.GONE
        if (::ivAiLogo.isInitialized) ivAiLogo.visibility = View.VISIBLE
        if (::pbLoading.isInitialized) pbLoading.visibility = View.GONE
    }

    private fun executeAiAction(actionType: String, textToProcess: String, tone: String?, customPrompt: String?) {
        val sanitizedText = PiiSanitizer.sanitize(textToProcess)
        val ic = currentInputConnection
        pendingReplaceLength = if (ic != null && ic.getSelectedText(0).isNullOrEmpty() && actionType != "CUSTOM_PROMPT" && actionType != "REPLY")
            textToProcess.length else 0
        tvPreviewText.setText(if (actionType == "CUSTOM_PROMPT" && customPrompt != null) "Asking: $customPrompt…" else textToProcess)
        tvPreviewText.setTextColor(theme.hintText)
        startAiThinkingDotsAnimation()
        previewZone.visibility = View.VISIBLE
        btnInsert.isEnabled = false
        mainLayout.findViewById<View>(R.id.btn_copy_response)?.visibility = View.GONE

        serviceScope.launch {
            val result = ApiClient.executeAction(this@AiKeyboardService, actionType, sanitizedText, tone, customInstruction = customPrompt)
            stopAiThinkingDotsAnimation()
            tvPreviewText.setTextColor(theme.keyText)
            result.onSuccess { response ->
                val replyQuestion = prefs.getBoolean("AI_REPLY_WITH_QUESTION", false)
                val qText = if (!customPrompt.isNullOrBlank()) customPrompt else if (actionType == "REPLY" && textToProcess.isNotBlank()) textToProcess else null
                val formattedText: CharSequence = if (replyQuestion && !qText.isNullOrBlank()) {
                    android.text.SpannableStringBuilder().apply {
                        val start = length
                        append(qText)
                        setSpan(
                            android.text.style.StyleSpan(android.graphics.Typeface.BOLD),
                            start,
                            length,
                            android.text.Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
                        )
                        append("\n\n")
                        append(response.resultText)
                    }
                } else {
                    response.resultText
                }
                activeAiResult = formattedText
                tvPreviewText.setText(formattedText, TextView.BufferType.SPANNABLE)
                tvPreviewText.isFocusable = true
                tvPreviewText.isFocusableInTouchMode = true
                tvPreviewText.isLongClickable = true
                btnInsert.isEnabled = true
                mainLayout.findViewById<View>(R.id.btn_copy_response)?.visibility = View.VISIBLE
            }.onFailure { err ->
                activeAiResult = null
                pendingReplaceLength = 0
                tvPreviewText.setText(err.message ?: "Error connecting to AI.")
                btnInsert.isEnabled = false
                mainLayout.findViewById<View>(R.id.btn_copy_response)?.visibility = View.GONE
            }
        }
    }

    // ================================================================== translation

    private val sourceLanguages = listOf("English", "Hindi", "Spanish", "French", "German", "Japanese")
    private val targetLanguages = listOf("Hindi", "English", "Spanish", "French", "German", "Japanese")

    private fun setupGifSearch() {
        val pb = mainLayout.findViewById<ProgressBar>(R.id.pb_gif_search_loading)
        val btnClear = mainLayout.findViewById<View>(R.id.btn_gif_search_clear)
        btnClear?.setOnClickListener { etGifSearchInput.setText("") }
        etGifSearchInput.showSoftInputOnFocus = false
        etGifSearchInput.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                btnClear?.visibility = if (s.isNullOrEmpty()) View.GONE else View.VISIBLE
            }
            override fun afterTextChanged(s: Editable?) {}
        })
        mainLayout.findViewById<View>(R.id.btn_gif_search_submit)?.setOnClickListener {
            val text = etGifSearchInput.text.toString()
            if (text.isBlank()) return@setOnClickListener
            currentGifQuery = text
            showPanel(Panel.GIF)
            gifHost.findViewById<android.widget.TextView>(R.id.et_gif_search)?.setText(currentGifQuery)
        }
    }

    private fun setupInlineTranslate() {
        val spSource = mainLayout.findViewById<Spinner>(R.id.sp_inline_source)
        val spTarget = mainLayout.findViewById<Spinner>(R.id.sp_inline_target)
        val pb = mainLayout.findViewById<ProgressBar>(R.id.pb_inline_loading)
        val btnClear = mainLayout.findViewById<View>(R.id.btn_inline_clear)
        spSource.adapter = themedSpinnerAdapter(sourceLanguages)
        spTarget.adapter = themedSpinnerAdapter(targetLanguages)
        mainLayout.findViewById<View>(R.id.btn_inline_swap)?.setOnClickListener {
            val s = spSource.selectedItemPosition
            spSource.setSelection(sourceLanguages.indexOf(targetLanguages[spTarget.selectedItemPosition]).coerceAtLeast(0))
            spTarget.setSelection(targetLanguages.indexOf(sourceLanguages[s]).coerceAtLeast(0))
        }
        btnClear.setOnClickListener { etInline.setText("") }
        etInline.showSoftInputOnFocus = false
        etInline.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                btnClear.visibility = if (s.isNullOrEmpty()) View.GONE else View.VISIBLE
                inlineTranslated = false
            }
            override fun afterTextChanged(s: Editable?) {}
        })
        mainLayout.findViewById<View>(R.id.btn_inline_insert)?.setOnClickListener {
            val text = etInline.text.toString()
            if (text.isBlank()) return@setOnClickListener
            if (inlineTranslated) {
                inlineZone.visibility = View.GONE
                currentInputConnection?.commitText(text, 1)
                inlineTranslated = false
                return@setOnClickListener
            }
            pb.visibility = View.VISIBLE
            translate(spSource.selectedItem.toString(), spTarget.selectedItem.toString(), text) { out, ok ->
                pb.visibility = View.GONE
                if (ok) {
                    etInline.setText(out)
                    etInline.setSelection(etInline.text.length)
                    inlineTranslated = true
                } else toast(out)
            }
        }
    }

    private fun setupTranslateView() {
        val spSource = translateView.findViewById<Spinner>(R.id.sp_translate_source)
        val spTarget = translateView.findViewById<Spinner>(R.id.sp_translate_target)
        val etSource = translateView.findViewById<EditText>(R.id.et_translate_source)
        val tvResult = translateView.findViewById<TextView>(R.id.tv_translate_result)
        val pb = translateView.findViewById<ProgressBar>(R.id.pb_translate_loading)
        val btnClear = translateView.findViewById<View>(R.id.btn_clear_source)
        val btnDo = translateView.findViewById<Button>(R.id.btn_do_translate)
        spSource.adapter = themedSpinnerAdapter(sourceLanguages)
        spTarget.adapter = themedSpinnerAdapter(targetLanguages)
        etSource.showSoftInputOnFocus = false
        translateView.findViewById<View>(R.id.btn_translate_back).setOnClickListener { showPanel(Panel.KEYS) }
        translateView.findViewById<View>(R.id.btn_translate_swap).setOnClickListener {
            val s = spSource.selectedItemPosition
            spSource.setSelection(sourceLanguages.indexOf(targetLanguages[spTarget.selectedItemPosition]).coerceAtLeast(0))
            spTarget.setSelection(targetLanguages.indexOf(sourceLanguages[s]).coerceAtLeast(0))
        }
        btnClear.setOnClickListener { etSource.setText(""); tvResult.text = "" }
        etSource.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                btnClear.visibility = if (s.isNullOrEmpty()) View.GONE else View.VISIBLE
            }
            override fun afterTextChanged(s: Editable?) {}
        })
        btnDo.setOnClickListener {
            val text = etSource.text.toString()
            if (text.isBlank()) return@setOnClickListener
            pb.visibility = View.VISIBLE
            tvResult.text = "Translating…"
            translate(spSource.selectedItem.toString(), spTarget.selectedItem.toString(), text) { out, _ ->
                pb.visibility = View.GONE
                tvResult.text = out
            }
        }
        translateView.findViewById<View>(R.id.btn_insert_translate).setOnClickListener {
            val out = tvResult.text.toString().takeIf { it.isNotBlank() && it != "Translating…" } ?: etSource.text.toString()
            if (out.isNotBlank()) {
                currentInputConnection?.commitText(out, 1)
                showPanel(Panel.KEYS)
            }
        }
    }

    private fun mlKitCode(lang: String): String = when (lang.lowercase()) {
        "hindi" -> TranslateLanguage.HINDI
        "spanish" -> TranslateLanguage.SPANISH
        "french" -> TranslateLanguage.FRENCH
        "german" -> TranslateLanguage.GERMAN
        "japanese" -> TranslateLanguage.JAPANESE
        else -> TranslateLanguage.ENGLISH
    }

    /** On-device ML Kit translation (downloads each language model once, then works offline). */
    private fun translate(src: String, dst: String, text: String, done: (String, Boolean) -> Unit) {
        if (src == dst) {
            done(text, true); return
        }
        val translator = Translation.getClient(
            TranslatorOptions.Builder().setSourceLanguage(mlKitCode(src)).setTargetLanguage(mlKitCode(dst)).build()
        )
        translator.downloadModelIfNeeded(DownloadConditions.Builder().build())
            .addOnSuccessListener {
                translator.translate(text)
                    .addOnSuccessListener { done(it, true); translator.close() }
                    .addOnFailureListener { done("Translation failed: ${it.message}", false); translator.close() }
            }
            .addOnFailureListener {
                done("Language pack download failed. Connect to the internet once to download it.", false)
                translator.close()
            }
    }

    // ================================================================== text editing panel

    private fun setupTextEditView() {
        val v = textEditView
        v.findViewById<View>(R.id.btn_text_edit_back).setOnClickListener { showPanel(Panel.KEYS) }

        // Select All (same as other apps)
        v.findViewById<View>(R.id.btn_edit_select_all).setOnClickListener {
            if (isInternalInputMode) {
                tvPreviewText.selectAll()
            } else {
                val ic = currentInputConnection
                val ok = ic?.performContextMenuAction(android.R.id.selectAll) ?: false
                if (!ok) {
                    try {
                        val et = ic?.getExtractedText(ExtractedTextRequest(), 0)
                        if (et != null && et.text != null) {
                            ic.setSelection(0, et.text.length)
                        }
                    } catch (_: Exception) {}
                }
            }
        }

        // Cut (same as other apps)
        v.findViewById<View>(R.id.btn_edit_cut)?.setOnClickListener {
            val selected = if (isInternalInputMode) {
                val s = minOf(tvPreviewText.selectionStart, tvPreviewText.selectionEnd)
                val e = maxOf(tvPreviewText.selectionStart, tvPreviewText.selectionEnd)
                if (s in 0 until e) tvPreviewText.text?.substring(s, e) else null
            } else {
                target().selectedText() ?: currentInputConnection?.getSelectedText(0)?.toString()
            }
            if (!selected.isNullOrEmpty()) {
                val cm = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                cm.setPrimaryClip(ClipData.newPlainText("text", selected))
                ClipboardStore.addText(this, selected)
                if (isInternalInputMode) {
                    val s = minOf(tvPreviewText.selectionStart, tvPreviewText.selectionEnd)
                    val e = maxOf(tvPreviewText.selectionStart, tvPreviewText.selectionEnd)
                    tvPreviewText.text?.delete(s, e)
                } else {
                    target().commitText("")
                }
                toast("Cut")
            } else {
                currentInputConnection?.performContextMenuAction(android.R.id.cut)
            }
        }

        // Copy (same as other apps)
        v.findViewById<View>(R.id.btn_edit_copy).setOnClickListener {
            val selected = if (isInternalInputMode) {
                val s = minOf(tvPreviewText.selectionStart, tvPreviewText.selectionEnd)
                val e = maxOf(tvPreviewText.selectionStart, tvPreviewText.selectionEnd)
                if (s in 0 until e) tvPreviewText.text?.substring(s, e) else null
            } else {
                target().selectedText() ?: currentInputConnection?.getSelectedText(0)?.toString()
            }
            if (!selected.isNullOrEmpty()) {
                val cm = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                cm.setPrimaryClip(ClipData.newPlainText("text", selected))
                ClipboardStore.addText(this, selected)
                toast("Copied")
            } else {
                currentInputConnection?.performContextMenuAction(android.R.id.copy)
                toast("Copied")
            }
        }

        // Paste (same as other apps - 100% reliable across all apps)
        v.findViewById<View>(R.id.btn_edit_paste).setOnClickListener {
            val cm = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            val clip = cm.primaryClip
            val text = if (clip != null && clip.itemCount > 0) {
                clip.getItemAt(0)?.coerceToText(this)?.toString()
            } else null

            if (!text.isNullOrEmpty()) {
                target().commitText(text)
            } else {
                currentInputConnection?.performContextMenuAction(android.R.id.paste)
            }
        }

        val selectBtn = v.findViewById<Button>(R.id.btn_edit_select_toggle)
        selectBtn?.setOnClickListener {
            selectMode = !selectMode
            selectBtn.text = if (selectMode) "Selecting" else "Select"
            selectBtn.background = if (selectMode) {
                Ui.keyBackground(this, theme, theme.accent)
            } else {
                Ui.keyBackground(this, theme, theme.specialKeyBg)
            }
            selectBtn.setTextColor(if (selectMode) theme.accentText else theme.keyText)
        }

        fun arrow(id: Int, code: Int) = v.findViewById<View>(id).setOnClickListener { sendArrow(code) }
        arrow(R.id.btn_arrow_up, KeyEvent.KEYCODE_DPAD_UP)
        arrow(R.id.btn_arrow_down, KeyEvent.KEYCODE_DPAD_DOWN)
        arrow(R.id.btn_arrow_left, KeyEvent.KEYCODE_DPAD_LEFT)
        arrow(R.id.btn_arrow_right, KeyEvent.KEYCODE_DPAD_RIGHT)
        v.findViewById<View>(R.id.btn_edit_backspace).setOnClickListener { target().deleteBackward() }
        v.findViewById<View>(R.id.btn_jump_start).setOnClickListener { sendArrow(KeyEvent.KEYCODE_MOVE_HOME, ctrl = true) }
        v.findViewById<View>(R.id.btn_jump_end).setOnClickListener { sendArrow(KeyEvent.KEYCODE_MOVE_END, ctrl = true) }
    }

    /** Arrow keys; with "Select" on they extend the selection (Shift held). */
    private fun sendArrow(code: Int, ctrl: Boolean = false) {
        if (isInternalInputMode) {
            val start = minOf(tvPreviewText.selectionStart, tvPreviewText.selectionEnd).coerceAtLeast(0)
            val end = maxOf(tvPreviewText.selectionStart, tvPreviewText.selectionEnd).coerceAtLeast(0)
            val len = tvPreviewText.text?.length ?: 0
            when (code) {
                KeyEvent.KEYCODE_DPAD_LEFT -> {
                    if (selectMode) tvPreviewText.setSelection(start, (end - 1).coerceAtLeast(0))
                    else tvPreviewText.setSelection((end - 1).coerceAtLeast(0))
                }
                KeyEvent.KEYCODE_DPAD_RIGHT -> {
                    if (selectMode) tvPreviewText.setSelection(start, (end + 1).coerceAtMost(len))
                    else tvPreviewText.setSelection((end + 1).coerceAtMost(len))
                }
                KeyEvent.KEYCODE_MOVE_HOME -> {
                    if (selectMode) tvPreviewText.setSelection(start, 0)
                    else tvPreviewText.setSelection(0)
                }
                KeyEvent.KEYCODE_MOVE_END -> {
                    if (selectMode) tvPreviewText.setSelection(start, len)
                    else tvPreviewText.setSelection(len)
                }
                else -> {
                    val now = SystemClock.uptimeMillis()
                    var meta = 0
                    if (selectMode) meta = meta or KeyEvent.META_SHIFT_ON or KeyEvent.META_SHIFT_LEFT_ON
                    tvPreviewText.dispatchKeyEvent(KeyEvent(now, now, KeyEvent.ACTION_DOWN, code, 0, meta))
                    tvPreviewText.dispatchKeyEvent(KeyEvent(now, now, KeyEvent.ACTION_UP, code, 0, meta))
                }
            }
            return
        }

        val ic = currentInputConnection ?: return
        var meta = 0
        if (selectMode) meta = meta or KeyEvent.META_SHIFT_ON or KeyEvent.META_SHIFT_LEFT_ON
        if (ctrl) meta = meta or KeyEvent.META_CTRL_ON or KeyEvent.META_CTRL_LEFT_ON
        val now = SystemClock.uptimeMillis()
        ic.sendKeyEvent(KeyEvent(now, now, KeyEvent.ACTION_DOWN, code, 0, meta))
        ic.sendKeyEvent(KeyEvent(now, now, KeyEvent.ACTION_UP, code, 0, meta))
    }

    // ================================================================== utils

    private fun toast(msg: String) = Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()

    companion object {
        /** lower -> Title Case -> UPPER -> lower */
        fun cycleCase(s: String): String {
            val lower = s.lowercase()
            val upper = s.uppercase()
            val title = titleCase(s)
            return when (s) {
                lower -> if (title != lower) title else upper
                title -> if (upper != title) upper else lower
                else -> lower
            }
        }

        fun titleCase(s: String): String {
            val sb = StringBuilder(s.length)
            var startOfWord = true
            for (c in s) {
                sb.append(if (startOfWord) c.uppercaseChar() else c.lowercaseChar())
                startOfWord = c.isWhitespace()
            }
            return sb.toString()
        }
    }
}
