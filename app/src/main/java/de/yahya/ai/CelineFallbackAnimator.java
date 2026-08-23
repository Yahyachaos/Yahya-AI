package de.yahya.ai;

import android.animation.ObjectAnimator;
import android.animation.ValueAnimator;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.AccelerateDecelerateInterpolator;
import android.widget.ImageView;

import java.util.Collections;
import java.util.Set;
import java.util.WeakHashMap;

/**
 * Guarantees visible motion for Celine even when a device rejects the Filament 3D surface.
 *
 * This deliberately animates properties that CelineAvatarController does not own on the
 * ImageView itself (rotationY / rotation / scale). The controller can therefore keep driving
 * gaze, blinking, speech mouth cues and state transitions while this layer supplies an obvious,
 * continuous "alive" motion instead of leaving a static portrait on screen.
 */
public final class CelineFallbackAnimator {
    private static final Set<ImageView> STARTED =
            Collections.newSetFromMap(new WeakHashMap<ImageView, Boolean>());

    private CelineFallbackAnimator() {}

    public static void ensure(View root) {
        if (root == null) return;
        ImageView avatar = findLargestImage(root, null);
        if (avatar == null || STARTED.contains(avatar)) return;
        STARTED.add(avatar);

        float density = avatar.getResources().getDisplayMetrics().density;
        avatar.setCameraDistance(Math.max(8000f, 10000f * density));
        avatar.setPivotX(avatar.getWidth() > 0 ? avatar.getWidth() * 0.50f : 0f);
        avatar.setPivotY(avatar.getHeight() > 0 ? avatar.getHeight() * 0.58f : 0f);

        ObjectAnimator turn = ObjectAnimator.ofFloat(avatar, View.ROTATION_Y,
                -2.2f, 1.6f, 2.2f, -1.0f, -2.2f);
        turn.setDuration(6500L);
        turn.setRepeatCount(ValueAnimator.INFINITE);
        turn.setInterpolator(new AccelerateDecelerateInterpolator());

        ObjectAnimator sway = ObjectAnimator.ofFloat(avatar, View.ROTATION,
                -0.32f, 0.24f, 0.38f, -0.16f, -0.32f);
        sway.setDuration(7200L);
        sway.setRepeatCount(ValueAnimator.INFINITE);
        sway.setInterpolator(new AccelerateDecelerateInterpolator());

        ObjectAnimator breatheX = ObjectAnimator.ofFloat(avatar, View.SCALE_X,
                1.000f, 1.010f, 1.016f, 1.007f, 1.000f);
        breatheX.setDuration(4600L);
        breatheX.setRepeatCount(ValueAnimator.INFINITE);
        breatheX.setInterpolator(new AccelerateDecelerateInterpolator());

        ObjectAnimator breatheY = ObjectAnimator.ofFloat(avatar, View.SCALE_Y,
                1.000f, 1.012f, 1.019f, 1.008f, 1.000f);
        breatheY.setDuration(4600L);
        breatheY.setRepeatCount(ValueAnimator.INFINITE);
        breatheY.setInterpolator(new AccelerateDecelerateInterpolator());

        turn.start();
        sway.start();
        breatheX.start();
        breatheY.start();
    }

    private static ImageView findLargestImage(View view, ImageView best) {
        if (view instanceof ImageView) {
            ImageView image = (ImageView) view;
            long area = (long) Math.max(0, image.getWidth()) * Math.max(0, image.getHeight());
            long bestArea = best == null ? -1L :
                    (long) Math.max(0, best.getWidth()) * Math.max(0, best.getHeight());
            if (area > bestArea) best = image;
        }
        if (view instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) view;
            for (int i = 0; i < group.getChildCount(); i++) {
                best = findLargestImage(group.getChildAt(i), best);
            }
        }
        return best;
    }
}
