package com.lzchyi.tvkeys;
import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
public final class LibraryRoute extends Activity {
    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        try {
            startActivity(new Intent("com.gathin.tvhome.action.APP_LIBRARY")
                .setClassName("com.gathin.tvhome", "com.gathin.tvhome.MainActivity")
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP));
            Log.i("TvKeys.Trial", "Library route launched");
        } catch (RuntimeException error) {
            Log.e("TvKeys.Trial", "Library route unavailable", error);
        } finally { finish(); }
    }
}
