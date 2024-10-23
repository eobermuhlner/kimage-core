package ch.obermuhlner.kimage.core.image.filter

class CopyFilter : MatrixImageFilter(
    {_, source ->
        source.copy()
    }
)