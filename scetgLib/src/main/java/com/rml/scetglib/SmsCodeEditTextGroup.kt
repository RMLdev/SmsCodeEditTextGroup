package com.rml.scetglib

import android.app.Activity
import android.content.Context
import android.graphics.Color
import android.graphics.drawable.Drawable
import android.os.Parcel
import android.os.Parcelable
import android.text.*
import android.text.InputFilter.LengthFilter
import android.util.AttributeSet
import android.util.Log
import android.view.Gravity
import android.view.KeyEvent
import android.view.View
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputConnection
import android.view.inputmethod.InputConnectionWrapper
import android.view.inputmethod.InputMethodManager
import android.widget.EditText
import androidx.annotation.ColorInt
import androidx.annotation.Px
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.constraintlayout.widget.ConstraintSet
import androidx.core.content.res.ResourcesCompat
import com.rml.scetglib.extensions.dpToPx
import com.rml.scetglib.extensions.spToPx


class SmsCodeEditTextGroup @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : ConstraintLayout(context, attrs, defStyleAttr),
    TextWatcher {
    private var code: String = ""
    private var inputState: Int = STATE_INPUT
    private var codeLength: Int = DEFAULT_CODE_LENGTH
    private var editTextViews: List<SmsCodeEditText>

    /** отвечает за расстояние между ячейками */

    @Px
    private var spacing: Float = context.dpToPx(DEFAULT_SPACING)

    /** ширина одной ячейки */
    @Px
    private var width: Float = context.dpToPx(DEFAULT_WIDTH)

    /** высота одной ячейки */
    @Px
    private var height: Float = context.dpToPx(DEFAULT_HEIGHT)

    /** размер текста в ячейке */
    @Px
    private var textSize: Float = context.spToPx(DEFAULT_TEXT_SIZE)

    /** цвет текста при правильном вводе */
    @ColorInt
    private var correctTextColor: Int = Color.GREEN

    /** цвет текста при неправильном вводе */
    @ColorInt
    private var incorrectTextColor: Int = Color.RED

    /** стандартный цвет текста */
    @ColorInt
    private var textColor: Int = Color.BLACK

    /** стандартный фон */
    private var bgDrawable: Drawable? = null

    /** фон при неправильном вводе */
    private var incorrectBgDrawable: Drawable? = null

    /** фон при правильном вводе */
    private var correctBgDrawable: Drawable? = null

    /** фон, когда ячейка находится в фокусе */
    private var focusedBgDrawable: Drawable? = null

    /** фон, когда ячейка содержит символ */
    private var filledBgDrawable: Drawable? = null

    /** выражение, которое будет вызвано, когда все ячейки заполнены символами
     * устанавливается методом setOnCodeEntered() */
    private lateinit var onCodeEntered: (code: String) -> Unit

    /** здесь сохраняются те значения, которые были описаны в XML файле */
    init {
        context.theme.obtainStyledAttributes(
            attrs,
            R.styleable.SmsCodeEditTextGroup,
            defStyleAttr,
            0
        ).apply {
            try {
                codeLength =
                    getInt(R.styleable.SmsCodeEditTextGroup_scetg_code_length, DEFAULT_CODE_LENGTH)
                spacing =
                    getDimension(
                        R.styleable.SmsCodeEditTextGroup_scetg_spacing,
                        context.dpToPx(DEFAULT_SPACING)
                    )
                width =
                    getDimension(
                        R.styleable.SmsCodeEditTextGroup_scetg_cell_width,
                        context.dpToPx(DEFAULT_WIDTH)
                    )
                height =
                    getDimension(
                        R.styleable.SmsCodeEditTextGroup_scetg_cell_height,
                        context.dpToPx(DEFAULT_HEIGHT)
                    )
                textSize =
                    getDimension(
                        R.styleable.SmsCodeEditTextGroup_scetg_textSize,
                        context.spToPx(DEFAULT_TEXT_SIZE)

                    )
                bgDrawable =
                    getDrawable(R.styleable.SmsCodeEditTextGroup_scetg_background)
                        ?: ResourcesCompat.getDrawable(
                            context.resources,
                            R.drawable.default_background,
                            null
                        )
                incorrectBgDrawable =
                    getDrawable(R.styleable.SmsCodeEditTextGroup_scetg_background_incorrect)
                        ?: ResourcesCompat.getDrawable(
                            context.resources,
                            R.drawable.default_background_incorrect,
                            null
                        )
                correctBgDrawable =
                    getDrawable(R.styleable.SmsCodeEditTextGroup_scetg_background_correct)
                        ?: ResourcesCompat.getDrawable(
                            context.resources,
                            R.drawable.default_background_correct,
                            null
                        )

                focusedBgDrawable =
                    getDrawable(R.styleable.SmsCodeEditTextGroup_scetg_background_focused)
                        ?: ResourcesCompat.getDrawable(
                            context.resources,
                            R.drawable.default_background_focused,
                            null
                        )

                filledBgDrawable =
                    getDrawable(R.styleable.SmsCodeEditTextGroup_scetg_background_filled)
                        ?: ResourcesCompat.getDrawable(
                            context.resources,
                            R.drawable.default_background_filled,
                            null
                        )
                textColor =
                    getColor(R.styleable.SmsCodeEditTextGroup_scetg_textСolor, DEFAULT_TEXT_COLOR)
                correctTextColor = getColor(
                    R.styleable.SmsCodeEditTextGroup_scetg_textColor_correct,
                    DEFAULT_TEXT_CORRECT_COLOR
                )
                incorrectTextColor = getColor(
                    R.styleable.SmsCodeEditTextGroup_scetg_textColor_incorrect,
                    DEFAULT_TEXT_INCORRECT_COLOR
                )
            } finally {
                recycle()
            }
        }
        /** создаем такое количество объектов SmsCodeEditText, которое было указано в параметре codeLength и добавляем их в эту ViewGroup */
        editTextViews = List(codeLength) {
            val editText = SmsCodeEditText(context, attrs, defStyleAttr)
            addView(editText)
            return@List editText
        }
    }

    lateinit var idArray: IntArray
    lateinit var weightsArray: FloatArray
    lateinit var set: ConstraintSet

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        // устанавливаем тот код, который был сохранен при перевороте экрана
        for (letterIndex in code.indices) {
            editTextViews[letterIndex].setText(code[letterIndex].toString())
        }

        idArray = IntArray(codeLength) { editTextViews[it].id }

        weightsArray = FloatArray(codeLength) { 1f }

        set = ConstraintSet()
        // восстанавливаем сохраненное состояние ввода
        when (inputState) {
            STATE_CORRECT -> setCorrectInput()
            STATE_INCORRECT -> setIncorrectInput()
            STATE_INPUT -> setDefaultInputState()
        }
    }

    override fun onLayout(changed: Boolean, left: Int, top: Int, right: Int, bottom: Int) {
        super.onLayout(changed, left, top, right, bottom)
        // узнаем размеры ячейки, которые зависят от ширины всей группы и количества ячеек
        val w = (getWidth() / codeLength) + (spacing * (1 - codeLength) / codeLength)
        // программно создаем HorizontalChain, которая состоит из созданных SmsCodeEditText
        set.apply {
            createHorizontalChain(
                ConstraintSet.PARENT_ID,
                ConstraintSet.LEFT,
                ConstraintSet.PARENT_ID,
                ConstraintSet.RIGHT,
                idArray,
                weightsArray,
                ConstraintSet.CHAIN_PACKED
            )
            for (editText in editTextViews) {
                if (editText != editTextViews.last()) {
                    setMargin(editText.id, ConstraintSet.END, spacing.toInt())
                }
                constrainHeight(editText.id, w.toInt())
                constrainWidth(editText.id, w.toInt())
            }
        }

        set.applyTo(this)
    }

    override fun onSaveInstanceState(): Parcelable =
        SavedState(super.onSaveInstanceState()).apply {
            savedCode = getCurrentCode()
            savedInputState = inputState
        }

    override fun onRestoreInstanceState(state: Parcelable?) {
        super.onRestoreInstanceState(state)
        if (state is SavedState) {
            code = state.savedCode
            inputState = state.savedInputState
        }
    }

    override fun beforeTextChanged(p0: CharSequence?, p1: Int, p2: Int, p3: Int) {}

    override fun onTextChanged(input: CharSequence?, p1: Int, p2: Int, count: Int) {
        // когда пользователь вводит символ с клавиатуры переключаем фокус с текущей ячейкеи (focusedChild)
        // на следующую ячейку (focusingChild), если ячейка последняя вызывается метод buildCode()
        if (input != null && count == 1) {
            val focusedChildIndex = editTextViews.indexOf(focusedChild)
            val focusingChildIndex = editTextViews.indexOf(focusedChild) + 1

            if (focusedChildIndex != editTextViews.lastIndex && focusedChildIndex != -1) {
                editTextViews[focusedChildIndex].clearFocus()
                editTextViews[focusingChildIndex].requestFocus()
            } else if (focusedChildIndex == editTextViews.lastIndex) {
                buildCode()
            }
        }
    }

    override fun afterTextChanged(p0: Editable?) {}

    /** вытравливает символы из каждой ячейки и создает строку с кодом, если onCodeEntered проинициализирован вызвает его с этой строкой, если нет выбрасывает исключение */
    private fun buildCode() {
        val isInputCompleted = editTextViews.none { it.text.isNullOrEmpty() }
        if (isInputCompleted) {
            code = getCurrentCode()
            try {
                onCodeEntered(code)
            } catch (e: UninitializedPropertyAccessException) {
                Log.e(
                    TAG,
                    "onCodeEntered action must be initialized. Use setOnCodeEntered method to initialize that."
                )
            }
        }
    }

    /** возвращает текущую строку из символов в ячейках */
    fun getCurrentCode(): String = editTextViews.joinToString(separator = "") { it.text.toString() }

    /** устанавливает код в View */
    fun setCode(code: String) {
        this.code = code
        for (charIndex in code.indices) {
            editTextViews[charIndex].setText(code[charIndex].toString())
        }
        try {
            if (code.isNotEmpty()) onCodeEntered(code)
        } catch (e: UninitializedPropertyAccessException) {
            Log.e(
                TAG,
                "onCodeEntered action must be initialized. Use setOnCodeEntered method to initialize that."
            )
        }
        editTextViews.forEach { it.clearFocus() }
    }

    /** позволяет определить действие, которое будет выполняться, когда код введен полностью */
    fun setOnCodeEntered(f: (code: String) -> Unit) {
        onCodeEntered = f
    }

    fun setCorrectInput() {
        inputState = STATE_CORRECT
        editTextViews.forEach {
            it.background = correctBgDrawable ?: bgDrawable
            it.setTextColor(correctTextColor)
            it.isFocusableInTouchMode = false
        }
        clearFocus()
        val imm: InputMethodManager =
            context.getSystemService(Activity.INPUT_METHOD_SERVICE) as InputMethodManager
        imm.hideSoftInputFromWindow(this.windowToken, 0)
    }

    fun setIncorrectInput() {
        inputState = STATE_INCORRECT
        editTextViews.forEach {
            it.background = incorrectBgDrawable ?: bgDrawable
            it.setTextColor(incorrectTextColor)
        }
    }

    fun resetInput() {
        editTextViews.forEach {
            it.setText("")
        }
        setDefaultInputState()
        setCode("")
    }

    fun setDefaultInputState() {
        inputState = STATE_INPUT
        editTextViews.forEach {
            it.background =
                if (isFocused) {
                    focusedBgDrawable
                } else if (!it.text.isNullOrEmpty()) {
                    filledBgDrawable
                } else {
                    bgDrawable
                }
            it.setTextColor(textColor)
        }
    }

    /** отвечает за сохранение информации при изменении параметров экрана */
    private class SavedState : BaseSavedState, Parcelable {
        var savedCode = ""
        var savedInputState = 0

        constructor(superState: Parcelable?) : super(superState)

        constructor(parcel: Parcel) : super(parcel) {
            savedCode = parcel.readString().toString()
            savedInputState = parcel.readInt()
        }

        override fun writeToParcel(parcel: Parcel, flags: Int) {
            super.writeToParcel(parcel, flags)
            parcel.writeString(savedCode)
            parcel.writeInt(savedInputState)
        }

        override fun describeContents(): Int = 0

        companion object CREATOR : Parcelable.Creator<SavedState> {
            override fun createFromParcel(parcel: Parcel): SavedState = SavedState(parcel)

            override fun newArray(size: Int): Array<SavedState?> = arrayOfNulls(size)
        }
    }

    /** Кастомный AppCompatEditText. Использует проинициализированные/сохраненные значения для определения вида ячейки. Устанавливает кастомный ConnectionWrapper */
    private inner class SmsCodeEditText(context: Context, attrs: AttributeSet?, defStyleAttr: Int) :
        androidx.appcompat.widget.AppCompatEditText(context, attrs, defStyleAttr) {
        init {
            id = View.generateViewId()
            background = bgDrawable
            gravity = Gravity.CENTER
            textSize = this@SmsCodeEditTextGroup.textSize
            inputType = InputType.TYPE_CLASS_NUMBER
            isFocusable = true
            isFocusableInTouchMode = true
            filters = arrayOf<InputFilter>(LengthFilter(1))
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
                textCursorDrawable = null
            }
            addTextChangedListener(this@SmsCodeEditTextGroup)
            setOnFocusChangeListener { view, isFocused ->
                if (view == this && inputState == STATE_INPUT) {
                    background =
                        if (isFocused) {
                            focusedBgDrawable
                        } else if (!text.isNullOrEmpty()) {
                            filledBgDrawable
                        } else {
                            bgDrawable
                        }
                }
            }
        }

        override fun onCreateInputConnection(outAttrs: EditorInfo): InputConnection {
            return SCETGInputConnectionWrapper(
                super.onCreateInputConnection(outAttrs)!!,
                true
            )
        }
    }

    /** Используется для того, чтобы слушать нажатие backspace с клавиатуры */
    private inner class SCETGInputConnectionWrapper(target: InputConnection, mutable: Boolean) :
        InputConnectionWrapper(target, mutable) {
        override fun sendKeyEvent(event: KeyEvent?): Boolean {
            if (event?.action == KeyEvent.ACTION_UP &&
                event.keyCode == KeyEvent.KEYCODE_DEL
            ) {
                if (inputState != STATE_INPUT && inputState != STATE_CORRECT) {
                    setDefaultInputState()
                }
                // перекидываем фокус с текущего элемента (focusedChild) на элемент назад (addressingEditText), стираем символ в нем
                val focusedChild = focusedChild as EditText?
                if (focusedChild != null && focusedChild.text.toString() == "") {
                    val addressingEditTextIndex = editTextViews.indexOf(focusedChild) - 1
                    if (addressingEditTextIndex >= 0) {
                        editTextViews[addressingEditTextIndex].requestFocus()
                        editTextViews[addressingEditTextIndex].setText("")
                        return false
                    }
                }
            }
            return super.sendKeyEvent(event)
        }
    }

    companion object {
        private const val TAG = "SmsCodeEditTextGroup"
        private const val STATE_INCORRECT = -1
        private const val STATE_CORRECT = 1
        private const val STATE_INPUT = 0
        private const val DEFAULT_CODE_LENGTH = 4
        private const val DEFAULT_SPACING = 2f
        private const val DEFAULT_WIDTH = 40f
        private const val DEFAULT_HEIGHT = 40f
        private const val DEFAULT_TEXT_SIZE = 24f
        private val DEFAULT_TEXT_COLOR = Color.parseColor("#4D9FD5")
        private val DEFAULT_TEXT_CORRECT_COLOR = Color.parseColor("#66BB6A")
        private val DEFAULT_TEXT_INCORRECT_COLOR = Color.parseColor("#EF5350")

    }
}


