package com.buddhanag.luminux.data

import android.graphics.Bitmap
import android.graphics.Matrix

object PhotoEditing {
    /**
     * Rotates counterclockwise by the recipe's turns, crops, then adjusts. [source] must be an upright software
     * bitmap; it is not changed. Used for the editor preview (on [PhotoRepository.editPreview]) and the saved copy.
     */
    fun render(source: Bitmap, recipe: EditRecipe, includingCrop: Boolean = true): Bitmap {
        // In Android's y-down space, counterclockwise is a negative rotation.
        val rotated = if (recipe.turns == 0) source else
            Bitmap.createBitmap(source, 0, 0, source.width, source.height, Matrix().apply { postRotate(-90f * recipe.turns) }, true)
        val cropped = if (!includingCrop || recipe.crop == NormRect.UNIT) rotated else {
            val r = recipe.cropRect(rotated.width, rotated.height)
            Bitmap.createBitmap(rotated, r.left, r.top, r.width, r.height)
        }
        if (recipe.adjustments.isNeutral) return cropped
        val out = if (cropped.isMutable && cropped !== source) cropped else cropped.copy(Bitmap.Config.ARGB_8888, true)
        val pixels = IntArray(out.width * out.height)
        out.getPixels(pixels, 0, out.width, 0, 0, out.width, out.height)
        recipe.adjustments.apply(pixels, out.width, out.height)
        out.setPixels(pixels, 0, out.width, 0, 0, out.width, out.height)
        return out
    }
}
