package net.osmand.plus.settings.preferences;

import android.content.Context;
import android.util.AttributeSet;

import androidx.preference.EditTextPreference;

public class EditTextPreferenceEx extends EditTextPreference {

	private String description;

	public EditTextPreferenceEx(Context context, AttributeSet attrs, int defStyleAttr, int defStyleRes) {
		super(context, attrs, defStyleAttr, defStyleRes);
	}

	public EditTextPreferenceEx(Context context, AttributeSet attrs, int defStyleAttr) {
		super(context, attrs, defStyleAttr);
	}

	public EditTextPreferenceEx(Context context, AttributeSet attrs) {
		super(context, attrs);
	}

	public EditTextPreferenceEx(Context context) {
		super(context);
	}

	public String getDescription() {
		return description;
	}

	public void setDescription(String description) {
		this.description = description;
	}

	public void setDescription(int descriptionResId) {
		setDescription(getContext().getString(descriptionResId));
	}

	@Override
	public CharSequence getDialogTitle() {
		CharSequence dialogTitle = super.getDialogTitle();
		return dialogTitle != null ? dialogTitle : getTitle();
	}

	@Override
	protected android.os.Parcelable onSaveInstanceState() {
		final android.os.Parcelable superState = super.onSaveInstanceState();
		if (isPersistent()) {
			return superState;
		}

		final SavedState myState = new SavedState(superState);
		myState.text = getText();
		return myState;
	}

	@Override
	protected void onRestoreInstanceState(android.os.Parcelable state) {
		if (state == null || !state.getClass().equals(SavedState.class)) {
			super.onRestoreInstanceState(state);
			return;
		}

		SavedState myState = (SavedState) state;
		super.onRestoreInstanceState(myState.getSuperState());
		setText(myState.text);
	}

	private static class SavedState extends BaseSavedState {
		String text;

		public SavedState(android.os.Parcelable superState) {
			super(superState);
		}

		public SavedState(android.os.Parcel android) {
			super(android);
			text = android.readString();
		}

		@Override
		public void writeToParcel(android.os.Parcel dest, int flags) {
			super.writeToParcel(dest, flags);
			dest.writeString(text);
		}

		public static final android.os.Parcelable.Creator<SavedState> CREATOR =
				new android.os.Parcelable.Creator<SavedState>() {
					public SavedState createFromParcel(android.os.Parcel in) {
						return new SavedState(in);
					}

					public SavedState[] newArray(int size) {
						return new SavedState[size];
					}
				};
	}
}
