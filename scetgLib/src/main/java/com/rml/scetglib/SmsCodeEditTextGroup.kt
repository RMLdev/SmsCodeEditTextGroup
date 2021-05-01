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

    @Px
    private var spacing: Float = context.dpToPx(DEFAULT_SPACING)

    @Px
    private var width: Float = context.dpToPx(DEFAULT_WIDTH)

    @Px
    private var height: Float = context.dpToPx(DEFAULT_HEIGHT)

    @Px
    private var textSize: Float = DEFAULT_TEXT_SIZE

    @ColorInt
    private var correctTextColor: Int = Color.GREEN

    @ColorInt
    private var incorrectTextColor: Int = Color.RED

    @ColorInt
    private var textColor: Int = Color.BLACK

    private var bgDrawable: Drawable? = null
    private var incorrectBgDrawable: Drawable? = null
    private var correctBgDrawable: Drawable? = null
    private var focusedBgDrawable: Drawable? = null
    private var filledBgDrawable: Drawable? = null


    private lateinit var onCodeEntered: (code: String) -> Unit


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
                        DEFAULT_TEXT_SIZE

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
        editTextViews = List(codeLength) {
            val editText = SmsCodeEditText(context, attrs, defStyleAttr)
            addView(editText)
            return@List editText
        }
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()

        for (letterIndex in code.indices) {
            editTextViews[letterIndex].setText(code[letterIndex].toString())
        }

        val idArray = IntArray(codeLength) { editTextViews[it].id }

        val weightsArray = FloatArray(codeLength) { 1f }

        val set = ConstraintSet()
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
                constrainHeight(editText.id, height.toInt())
                constrainWidth(editText.id, width.toInt())
            }
        }

        set.applyTo(this)
        when (inputState) {
            STATE_CORRECT -> setCorrectInput()
            STATE_INCORRECT -> setIncorrectInput()
            STATE_INPUT -> setDefaultInputState()
        }
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

    fun getCurrentCode(): String = editTextViews.joinToString(separator = "") { it.text.toString() }

    fun setCode(code: String) {
        this.code = code
        for (charIndex in code.indices) {
            editTextViews[charIndex].setText(code[charIndex].toString())
        }
        try {
            onCodeEntered(code)
        } catch (e: UninitializedPropertyAccessException) {
            Log.e(
                TAG,
                "onCodeEntered action must be initialized. Use setOnCodeEntered method to initialize that."
            )
        }
        editTextViews.forEach { it.clearFocus() }
    }

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


        override fun onCreateInputConnection(outAttrs: EditorInfo?): InputConnection {
            return SCETGInputConnectionWrapper(
                super.onCreateInputConnection(outAttrs),
                true
            )
        }
    }

    private inner class SCETGInputConnectionWrapper(target: InputConnection, mutable: Boolean) :
        InputConnectionWrapper(target, mutable) {
        override fun sendKeyEvent(event: KeyEvent?): Boolean {
            if (event?.action == KeyEvent.ACTION_UP
                && event.keyCode == KeyEvent.KEYCODE_DEL
            ) {
                if (inputState != STATE_INPUT && inputState != STATE_CORRECT) {
                    setDefaultInputState()
                }

                val focusedChild = focusedChild as EditText?
                if (focusedChild != null && focusedChild.text.toString() == "") {
                    val addressingEditTextIndex = editTextViews.indexOf(focusedChild) - 1
                    if (addressingEditTextIndex >= 0) {
                        editTextViews[addressingEditTextIndex].requestFocus()
                        editTextViews[addressingEditTextIndex].setSelection(1)
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


