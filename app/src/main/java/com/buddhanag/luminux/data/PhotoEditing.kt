package com.buddhanag.luminux.data

import android.graphics.Bitmap
import android.graphics.Matrix

object PhotoEditing {
    /**
     * Rotates counterclockwise by the recipe's turns, mirrors, straightens, crops, then adjusts. [source] must be an
     * upright software bitmap; it is not changed. Used for the editor preview (on [PhotoRepository.editPreview]) and
     * the saved copy.
     */
    fun render(source: Bitmap, recipe: EditRecipe, includingCrop: Boolean = true): Bitmap {
        // In Android's y-down space, counterclockwise is a negative rotation; the mirror comes after the turns.
        val turned = if (recipe.turns == 0 && !recipe.flipped) source else {
            val matrix = Matrix().apply {
                postRotate(-90f * recipe.turns)
                if (recipe.flipped) postScale(-1f, 1f)
            }
            Bitmap.createBitmap(source, 0, 0, source.width, source.height, matrix, true)
        }
        val straightened = if (recipe.straighten == 0.0) turned else {
            val w = turned.width; val h = turned.height
            val pixels = IntArray(w * h)
            turned.getPixels(pixels, 0, w, 0, 0, w, h)
            Bitmap.createBitmap(Straighten.apply(pixels, w, h, recipe.straighten), w, h, Bitmap.Config.ARGB_8888)
        }
        val cropped = if (!includingCrop || recipe.crop == NormRect.UNIT) straightened else {
            val r = recipe.cropRect(straightened.width, straightened.height)
            Bitmap.createBitmap(straightened, r.left, r.top, r.width, r.height)
        }
        if (recipe.adjustments.isNeutral) return cropped
        val out = if (cropped.isMutable && cropped !== source) cropped else cropped.copy(Bitmap.Config.ARGB_8888, true)
        val pixels = IntArray(out.width * out.height)
        out.getPixels(pixels, 0, out.width, 0, 0, out.width, out.height)
        recipe.adjustments.apply(pixels, out.width, out.height)
        out.setPixels(pixels, 0, out.width, 0, 0, out.width, out.height)
        return out
    }

    /** The straightening that levels the horizon in [source] edited by [recipe] before straightening and adjusting. */
    fun levellingAngle(source: Bitmap, recipe: EditRecipe): Double? {
        val shown = render(source, recipe.copy(straighten = 0.0, adjustments = Adjustments()), includingCrop = false)
        val pixels = IntArray(shown.width * shown.height)
        shown.getPixels(pixels, 0, shown.width, 0, 0, shown.width, shown.height)
        return Levelling.angle(pixels, shown.width, shown.height)
    }
}
