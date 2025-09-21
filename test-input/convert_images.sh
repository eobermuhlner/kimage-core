#!/bin/bash

# Input image
input_image="flowers.png"

# Define output formats
formats=("png" "tif" "bmp" "gif" "jpg")

# Define bit depths
depths=(8 16 32)

# Define color spaces
color_spaces=("RGB" "Gray")

# Loop through each combination of depth, color space, and format
for depth in "${depths[@]}"; do
    for color_space in "${color_spaces[@]}"; do
        for format in "${formats[@]}"; do
            # Define the output filename
            output_image="output_${color_space}_${depth}bit.${format}"

            # Set color space and depth
            if [ "$color_space" == "RGB" ]; then
                convert "$input_image" -depth "$depth" -colorspace RGB "$output_image"
            elif [ "$color_space" == "Gray" ]; then
                convert "$input_image" -depth "$depth" -colorspace Gray "$output_image"
            fi

            # Log output
            echo "Created $output_image"
        done
    done
done

echo "All conversions are done."
