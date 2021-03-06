package com.jakewharton.telecine;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.annotation.SuppressLint;
import android.content.Context;
import android.content.res.Resources;
import android.support.annotation.NonNull;
import android.view.Gravity;
import android.view.View;
import android.view.WindowManager;
import android.view.animation.AccelerateInterpolator;
import android.view.animation.DecelerateInterpolator;
import android.widget.FrameLayout;
import android.widget.TextView;
import butterknife.ButterKnife;
import butterknife.InjectView;
import butterknife.OnClick;
import java.util.Locale;

import static android.graphics.PixelFormat.TRANSLUCENT;
import static android.text.TextUtils.getLayoutDirectionFromLocale;
import static android.view.ViewAnimationUtils.createCircularReveal;
import static android.view.WindowManager.LayoutParams.FLAG_LAYOUT_INSET_DECOR;
import static android.view.WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN;
import static android.view.WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS;
import static android.view.WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE;
import static android.view.WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL;
import static android.view.WindowManager.LayoutParams.TYPE_SYSTEM_ERROR;

@SuppressLint("ViewConstructor") // Lint, in this case, I am smarter than you.
final class OverlayView extends FrameLayout {
  private static final int COUNTDOWN_DELAY = 1000;
  private static final int NON_COUNTDOWN_DELAY = 500;
  private static final int DURATION_ENTER_EXIT = 300;

  static OverlayView create(Context context, Listener listener, boolean showCountDown) {
    return new OverlayView(context, listener, showCountDown);
  }

  static WindowManager.LayoutParams createLayoutParams(Context context) {
    Resources res = context.getResources();
    int width = res.getDimensionPixelSize(R.dimen.overlay_width);
    int height = res.getDimensionPixelSize(R.dimen.overlay_height);
    final WindowManager.LayoutParams params =
        new WindowManager.LayoutParams(width, height, TYPE_SYSTEM_ERROR, FLAG_NOT_FOCUSABLE
            | FLAG_NOT_TOUCH_MODAL
            | FLAG_LAYOUT_NO_LIMITS
            | FLAG_LAYOUT_INSET_DECOR
            | FLAG_LAYOUT_IN_SCREEN, TRANSLUCENT);
    params.gravity = Gravity.TOP | gravityEndLocaleHack();

    return params;
  }

  @SuppressLint("RtlHardcoded") // Gravity.END is not honored by WindowManager for added views.
  private static int gravityEndLocaleHack() {
    int direction = getLayoutDirectionFromLocale(Locale.getDefault());
    return direction == LAYOUT_DIRECTION_RTL ? Gravity.LEFT : Gravity.RIGHT;
  }

  interface Listener {
    /** Called when cancel is clicked. This view is unusable once this callback is invoked. */
    void onCancel();

    /**
     * Called when start is clicked and it is appropriate to start recording. This view will hide
     * itself completely before invoking this callback.
     */
    void onStart();

    /** Called when stop is clicked. This view is unusable once this callback is invoked. */
    void onStop();
  }

  @InjectView(R.id.record_overlay_buttons) View buttonsView;
  @InjectView(R.id.record_overlay_cancel) View cancelView;
  @InjectView(R.id.record_overlay_start) View startView;
  @InjectView(R.id.record_overlay_stop) View stopView;
  @InjectView(R.id.record_overlay_recording) TextView recordingView;

  private final Listener listener;
  private final boolean showCountDown;
  private final int animationWidth;

  private OverlayView(Context context, Listener listener, boolean showCountDown) {
    super(context);
    this.listener = listener;
    this.showCountDown = showCountDown;

    int width = getResources().getDimensionPixelSize(R.dimen.overlay_width);
    if (getLayoutDirectionFromLocale(Locale.getDefault()) == LAYOUT_DIRECTION_RTL) {
      width = -width; // Account for animating in from the other side of the screen.
    }
    animationWidth = width;

    inflate(context, R.layout.overlay_view, this);
    ButterKnife.inject(this);
    CheatSheet.setup(cancelView);
    CheatSheet.setup(startView);
  }

  @Override protected void onAttachedToWindow() {
    super.onAttachedToWindow();

    setTranslationX(animationWidth);
    animate().translationX(0)
        .setDuration(DURATION_ENTER_EXIT)
        .setInterpolator(new DecelerateInterpolator());
  }

  @OnClick(R.id.record_overlay_cancel) void onCancelClicked() {
    animate().translationX(animationWidth)
        .setDuration(DURATION_ENTER_EXIT)
        .setInterpolator(new AccelerateInterpolator())
        .withEndAction(new Runnable() {
              @Override public void run() {
                listener.onCancel();
              }
            });
  }

  @OnClick(R.id.record_overlay_start) void onStartClicked() {
    recordingView.setVisibility(VISIBLE);
    int centerX = (int) (startView.getX() + (startView.getWidth() / 2));
    int centerY = (int) (startView.getY() + (startView.getHeight() / 2));
    Animator reveal = createCircularReveal(recordingView, centerX, centerY, 0, getWidth() / 2f);
    reveal.addListener(new AnimatorListenerAdapter() {
      @Override public void onAnimationEnd(Animator animation) {
        buttonsView.setVisibility(GONE);
      }
    });
    reveal.start();

    postDelayed(new Runnable() {
      @Override public void run() {
        if (showCountDown) {
          showCountDown();
        } else {
          recordingView.animate()
              .alpha(0)
              .setDuration(NON_COUNTDOWN_DELAY)
              .withEndAction(new Runnable() {
                @Override public void run() {
                  startRecording();
                }
              });
        }
      }
    }, showCountDown ? COUNTDOWN_DELAY : NON_COUNTDOWN_DELAY);
  }

  private void startRecording() {
    recordingView.setVisibility(INVISIBLE);
    stopView.setVisibility(VISIBLE);
    stopView.setOnClickListener(new OnClickListener() {
      @Override public void onClick(@NonNull View v) {
        listener.onStop();
      }
    });
    listener.onStart();
  }

  private void showCountDown() {
    recordingView.setText(R.string.countdown_three);
    postDelayed(new Runnable() {
      @Override public void run() {
        recordingView.setText(R.string.countdown_two);
        postDelayed(new Runnable() {
          @Override public void run() {
            recordingView.setText(R.string.countdown_one);
            recordingView.animate()
                .alpha(0)
                .setDuration(COUNTDOWN_DELAY)
                .withEndAction(new Runnable() {
                  @Override public void run() {
                    startRecording();
                  }
                });
          }
        }, COUNTDOWN_DELAY);
      }
    }, COUNTDOWN_DELAY);
  }
}
