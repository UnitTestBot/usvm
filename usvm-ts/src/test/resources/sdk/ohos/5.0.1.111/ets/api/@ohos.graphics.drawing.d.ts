/*
 * Copyright (c) 2023-2024 Huawei Device Co., Ltd.
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
/**
 * @file
 * @kit ArkGraphics2D
 */
import type image from './@ohos.multimedia.image';
import type common2D from './@ohos.graphics.common2D';
/**
 * Provides functions such as 2D graphics rendering, text drawing, and image display.
 *
 * @namespace drawing
 * @syscap SystemCapability.Graphics.Drawing
 * @since 11
 */
declare namespace drawing {
    /**
     * Enumerate blending modes for colors.
     * Blend is a operation that use 4 components(red, green, blue, alpha) to generate
     * a new color from two colors(source, destination).
     * @enum { number }
     * @syscap SystemCapability.Graphics.Drawing
     * @since 11
     */
    enum BlendMode {
        /**
         * Disable 4 regions(red, green, blue, alpha)
         * @syscap SystemCapability.Graphics.Drawing
         * @since 11
         */
        CLEAR = 0,
        /**
         * Use components of the source
         * @syscap SystemCapability.Graphics.Drawing
         * @since 11
         */
        SRC = 1,
        /**
         * Use components of the destination
         * @syscap SystemCapability.Graphics.Drawing
         * @since 11
         */
        DST = 2,
        /**
         * The source is placed above the destination.
         * @syscap SystemCapability.Graphics.Drawing
         * @since 11
         */
        SRC_OVER = 3,
        /**
         * The Destination is placed above the source.
         * @syscap SystemCapability.Graphics.Drawing
         * @since 11
         */
        DST_OVER = 4,
        /**
         * Use source replaces the destination, and will not exceed the boundaries of the destination
         * @syscap SystemCapability.Graphics.Drawing
         * @since 11
         */
        SRC_IN = 5,
        /**
         * Use destination, and will not exceed the boundaries of the source
         * @syscap SystemCapability.Graphics.Drawing
         * @since 11
         */
        DST_IN = 6,
        /**
         * Source is use in outside of the boundaries of the destination.
         * @syscap SystemCapability.Graphics.Drawing
         * @since 11
         */
        SRC_OUT = 7,
        /**
         * Destination is use in outside of the boundaries of the source.
         * @syscap SystemCapability.Graphics.Drawing
         * @since 11
         */
        DST_OUT = 8,
        /**
         * Source which overlaps the destination will replaces the destination.
         * @syscap SystemCapability.Graphics.Drawing
         * @since 11
         */
        SRC_ATOP = 9,
        /**
         * Destination which overlaps the source will replaces the source.
         * @syscap SystemCapability.Graphics.Drawing
         * @since 11
         */
        DST_ATOP = 10,
        /**
         * Combine regions where source and destination do not overlap.
         * @syscap SystemCapability.Graphics.Drawing
         * @since 11
         */
        XOR = 11,
        /**
         * The sum of the source and destination.
         * @syscap SystemCapability.Graphics.Drawing
         * @since 11
         */
        PLUS = 12,
        /**
         * All components are multiplied.
         * @syscap SystemCapability.Graphics.Drawing
         * @since 11
         */
        MODULATE = 13,
        /**
         * Multiply the complement values of the background and source color values,
         * and then complement the result.
         * @syscap SystemCapability.Graphics.Drawing
         * @since 11
         */
        SCREEN = 14,
        /**
         * Multiplies or screens the colors, depending on destination
         * @syscap SystemCapability.Graphics.Drawing
         * @since 11
         */
        OVERLAY = 15,
        /**
         * Choose a darker background and source color.
         * @syscap SystemCapability.Graphics.Drawing
         * @since 11
         */
        DARKEN = 16,
        /**
         * Choose a lighter background and source color.
         * @syscap SystemCapability.Graphics.Drawing
         * @since 11
         */
        LIGHTEN = 17,
        /**
         * Brightens destination color to reflect the source color.
         * @syscap SystemCapability.Graphics.Drawing
         * @since 11
         */
        COLOR_DODGE = 18,
        /**
         * Darkens destination color to reflect the source color.
         * @syscap SystemCapability.Graphics.Drawing
         * @since 11
         */
        COLOR_BURN = 19,
        /**
         * Multiplies or screens the colors, depending on source
         * @syscap SystemCapability.Graphics.Drawing
         * @since 11
         */
        HARD_LIGHT = 20,
        /**
         * Lightens or Darkens the colors, depending on the source.
         * @syscap SystemCapability.Graphics.Drawing
         * @since 11
         */
        SOFT_LIGHT = 21,
        /**
         * Subtract the darker of the two colors from the brighter color.
         * @syscap SystemCapability.Graphics.Drawing
         * @since 11
         */
        DIFFERENCE = 22,
        /**
         * Produces an effect similar to difference mode, but with lower contrast.
         * @syscap SystemCapability.Graphics.Drawing
         * @since 11
         */
        EXCLUSION = 23,
        /**
         * Multiply the source color by the destination color and replace the destination.
         * @syscap SystemCapability.Graphics.Drawing
         * @since 11
         */
        MULTIPLY = 24,
        /**
         * Use the hue of the source and the saturation and brightness of the destination.
         * @syscap SystemCapability.Graphics.Drawing
         * @since 11
         */
        HUE = 25,
        /**
         * Use the saturation of the source and the hue and brightness of the destination.
         * @syscap SystemCapability.Graphics.Drawing
         * @since 11
         */
        SATURATION = 26,
        /**
         * Use the hue and saturation of the source and the brightness of the destination.
         * @syscap SystemCapability.Graphics.Drawing
         * @since 11
         */
        COLOR = 27,
        /**
         * Use the brightness of the source and the hue and saturation of the destination.
         * @syscap SystemCapability.Graphics.Drawing
         * @since 11
         */
        LUMINOSITY = 28
    }
    /**
     * Enumerates direction for adding closed contours.
     * @enum { number }
     * @syscap SystemCapability.Graphics.Drawing
     * @since 12
     */
    enum PathDirection {
        /**
         * Clockwise direction for adding closed contours.
         * @syscap SystemCapability.Graphics.Drawing
         * @since 12
         */
        CLOCKWISE = 0,
        /**
         * Counter-clockwise direction for adding closed contours.
         * @syscap SystemCapability.Graphics.Drawing
         * @since 12
         */
        COUNTER_CLOCKWISE = 1
    }
    /**
     * Enumerates fill type of path.
     * @enum { number }
     * @syscap SystemCapability.Graphics.Drawing
     * @since 12
     */
    enum PathFillType {
        /**
         * Specifies that "inside" is computed by a non-zero sum of signed edge crossings.
         * @syscap SystemCapability.Graphics.Drawing
         * @since 12
         */
        WINDING = 0,
        /**
         * Specifies that "inside" is computed by an odd number of edge crossings.
         * @syscap SystemCapability.Graphics.Drawing
         * @since 12
         */
        EVEN_ODD = 1,
        /**
         * Same as winding, but draws outside of the path, rather than inside.
         * @syscap SystemCapability.Graphics.Drawing
         * @since 12
         */
        INVERSE_WINDING = 2,
        /**
         * Same as evenOdd, but draws outside of the path, rather than inside.
         * @syscap SystemCapability.Graphics.Drawing
         * @since 12
         */
        INVERSE_EVEN_ODD = 3
    }
    /**
    * Enumerate path measure flags for matrix.
    * @enum { number }
    * @syscap SystemCapability.Graphics.Drawing
    * @since 12
    */
    enum PathMeasureMatrixFlags {
        /**
         * Gets position.
         * @syscap SystemCapability.Graphics.Drawing
         * @since 12
         */
        GET_POSITION_MATRIX = 0,
        /**
         * Gets tangent.
         * @syscap SystemCapability.Graphics.Drawing
         * @since 12
         */
        GET_TANGENT_MATRIX = 1,
        /**
         * Gets both position and tangent.
         * @syscap SystemCapability.Graphics.Drawing
         * @since 12
         */
        GET_POSITION_AND_TANGENT_MATRIX = 2
    }
    /**
     * Provides the definition of the roundRect.
     *
     * @syscap SystemCapability.Graphics.Drawing
     * @since 12
     */
    class RoundRect {
        /**
         * Creates a simple round rect with the same four corner radii.
         * @param { common2D.Rect } rect - Indicates the Rect object.
         * @param { number } xRadii - Indicates the corner radii on x-axis.
         * @param { number } yRadii - Indicates the corner radii on y-axis.
         * @throws { BusinessError } 401 - Parameter error. Possible causes: 1. Mandatory parameters are left unspecified;
         * <br>2. Incorrect parameter types.
         * @syscap SystemCapability.Graphics.Drawing
         * @since 12
         */
        constructor(rect: common2D.Rect, xRadii: number, yRadii: number);
        /**
         * Sets the radiusX and radiusY for a specific corner position.
         * @param { CornerPos } pos - Indicates the corner radius position.
         * @param { number } x - Indicates the corner radius on x-axis.
         * @param { number } y - Indicates the corner radius on y-axis.
         * @throws { BusinessError } 401 - Parameter error. Possible causes: 1. Mandatory parameters are left unspecified;
         * <br>2. Incorrect parameter types; 3. Parameter verification failed.
         * @syscap SystemCapability.Graphics.Drawing
         * @since 12
         */
        setCorner(pos: CornerPos, x: number, y: number): void;
        /**
         * Gets a point with the values of x-axis and y-axis of the selected corner radius.
         * @param { CornerPos } pos - Indicates the corner radius position.
         * @returns { common2D.Point } Returns a point with the values of x-axis and y-axis of the corner radius.
         * @throws { BusinessError } 401 - Parameter error. Possible causes: 1. Mandatory parameters are left unspecified;
         * <br>2. Incorrect parameter types; 3. Parameter verification failed.
         * @syscap SystemCapability.Graphics.Drawing
         * @since 12
         */
        getCorner(pos: CornerPos): common2D.Point;
        /**
         * Translates round rect by (dx, dy).
         * @param { number } dx - Indicates the offsets added to rect left and rect right.
         * @param { number } dy - Indicates the offsets added to rect top and rect bottom.
         * @throws { BusinessError } 401 - Parameter error. Possible causes: 1. Mandatory parameters are left unspecified;
         * <br>2. Incorrect parameter types.
         * @syscap SystemCapability.Graphics.Drawing
         * @since 12
         */
        offset(dx: number, dy: number): void;
    }
    /**
     * Enumerates of operations when two paths are combined.
     * @enum { number }
     * @syscap SystemCapability.Graphics.Drawing
     * @since 12
     */
    enum PathOp {
        /**
         * Difference operation.
         * @syscap SystemCapability.Graphics.Drawing
         * @since 12
         */
        DIFFERENCE = 0,
        /**
         * Intersect operation.
         * @syscap SystemCapability.Graphics.Drawing
         * @since 12
         */
        INTERSECT = 1,
        /**
         * Union operation.
         * @syscap SystemCapability.Graphics.Drawing
         * @since 12
         */
        UNION = 2,
        /**
         * Xor operation.
         * @syscap SystemCapability.Graphics.Drawing
         * @since 12
         */
        XOR = 3,
        /**
         * Reverse difference operation.
         * @syscap SystemCapability.Graphics.Drawing
         * @since 12
         */
        REVERSE_DIFFERENCE = 4
    }
    /**
     * Describes a path object.
     *
     * @syscap SystemCapability.Graphics.Drawing
     * @since 11
     */
    class Path {
        /**
         * Creates a Path.
         * @syscap SystemCapability.Graphics.Drawing
         * @since 12
         */
        constructor();
        /**
         * Creates a Path from other path.
         * @param { Path } path - the path to copy content from.
         * @syscap SystemCapability.Graphics.Drawing
         * @since 12
         */
        constructor(path: Path);
        /**
         * Sets the start point of a path
         * @param { number } x - Indicates the x coordinate of the start point.
         * @param { number } y - Indicates the y coordinate of the start point.
         * @throws { BusinessError } 401 - Parameter error. Possible causes: 1. Mandatory parameters are left unspecified;
         * <br>2. Incorrect parameter types.
         * @syscap SystemCapability.Graphics.Drawing
         * @since 11
         */
        moveTo(x: number, y: number): void;
        /**
         * Draws a line segment from the last point of a path to the target point.
         * @param { number } x - Indicates the x coordinate of the target point.
         * @param { number } y - Indicates the y coordinate of the target point.
         * @throws { BusinessError } 401 - Parameter error. Possible causes: 1. Mandatory parameters are left unspecified;
         * <br>2. Incorrect parameter types.
         * @syscap SystemCapability.Graphics.Drawing
         * @since 11
         */
        lineTo(x: number, y: number): void;
        /**
         * This is done by using angle arc mode. In this mode, a rectangle that encloses an ellipse is specified first,
         * and then a start angle and a sweep angle are specified.
         * The arc is a portion of the ellipse defined by the start angle and the sweep angle.
         * By default, a line segment from the last point of the path to the start point of the arc is also added.
         * @param { number } x1 - Indicates the x coordinate of the upper left corner of the rectangle.
         * @param { number } y1 - Indicates the y coordinate of the upper left corner of the rectangle.
         * @param { number } x2 - Indicates the x coordinate of the lower right corner of the rectangle.
         * @param { number } y2 - Indicates the y coordinate of the lower right corner of the rectangle.
         * @param { number } startDeg - Indicates the start angle, in degrees.
         * @param { number } sweepDeg - Indicates the angle to sweep, in degrees.
         * @throws { BusinessError } 401 - Parameter error. Possible causes: 1. Mandatory parameters are left unspecified;
         * <br>2. Incorrect parameter types.
         * @syscap SystemCapability.Graphics.Drawing
         * @since 11
         */
        arcTo(x1: number, y1: number, x2: number, y2: number, startDeg: number, sweepDeg: number): void;
        /**
         * Draws a quadratic Bezier curve from the last point of a path to the target point.
         * @param { number } ctrlX - Indicates the x coordinate of the control point.
         * @param { number } ctrlY - Indicates the y coordinate of the control point.
         * @param { number } endX - Indicates the x coordinate of the target point.
         * @param { number } endY - Indicates the y coordinate of the target point.
         * @throws { BusinessError } 401 - Parameter error. Possible causes: 1. Mandatory parameters are left unspecified;
         * <br>2. Incorrect parameter types.
         * @syscap SystemCapability.Graphics.Drawing
         * @since 11
         */
        quadTo(ctrlX: number, ctrlY: number, endX: number, endY: number): void;
        /**
         * Draws a conic from the last point of a path to the target point.
         * @param { number } ctrlX - Indicates the x coordinate of the control point.
         * @param { number } ctrlY - Indicates the y coordinate of the control point.
         * @param { number } endX - Indicates the x coordinate of the target point.
         * @param { number } endY - Indicates the y coordinate of the target point.
         * @param { number } weight - Indicates the weight of added conic.
         * @throws { BusinessError } 401 - Parameter error. Possible causes: 1. Mandatory parameters are left unspecified;
         * <br>2. Incorrect parameter types.
         * @syscap SystemCapability.Graphics.Drawing
         * @since 12
         */
        conicTo(ctrlX: number, ctrlY: number, endX: number, endY: number, weight: number): void;
        /**
         * Draws a cubic Bezier curve from the last point of a path to the target point.
         * @param { number } ctrlX1 - Indicates the x coordinate of the first control point.
         * @param { number } ctrlY1 - Indicates the y coordinate of the first control point.
         * @param { number } ctrlX2 - Indicates the x coordinate of the second control point.
         * @param { number } ctrlY2 - Indicates the y coordinate of the second control point.
         * @param { number } endX - Indicates the x coordinate of the target point.
         * @param { number } endY - Indicates the y coordinate of the target point.
         * @throws { BusinessError } 401 - Parameter error. Possible causes: 1. Mandatory parameters are left unspecified;
         * <br>2. Incorrect parameter types.
         * @syscap SystemCapability.Graphics.Drawing
         * @since 11
         */
        cubicTo(ctrlX1: number, ctrlY1: number, ctrlX2: number, ctrlY2: number, endX: number, endY: number): void;
        /**
         * Sets the relative starting point of a path.
         * @param { number } dx - Indicates the x coordinate of the relative starting point.
         * @param { number } dy - Indicates the y coordinate of the relative starting point.
         * @throws { BusinessError } 401 - Parameter error. Possible causes: 1. Mandatory parameters are left unspecified;
         * <br>2. Incorrect parameter types.
         * @syscap SystemCapability.Graphics.Drawing
         * @since 12
         */
        rMoveTo(dx: number, dy: number): void;
        /**
         * Draws a line segment from the last point of a path to the relative target point.
         * @param { number } dx - Indicates the x coordinate of the relative target point.
         * @param { number } dy - Indicates the y coordinate of the relative target point.
         * @throws { BusinessError } 401 - Parameter error. Possible causes: 1. Mandatory parameters are left unspecified;
         * <br>2. Incorrect parameter types.
         * @syscap SystemCapability.Graphics.Drawing
         * @since 12
         */
        rLineTo(dx: number, dy: number): void;
        /**
         * Draws a quadratic bezier curve from the last point of a path to the relative target point.
         * @param { number } dx1 - Indicates the x coordinate of the relative control point.
         * @param { number } dy1 - Indicates the y coordinate of the relative control point.
         * @param { number } dx2 - Indicates the x coordinate of the relative target point.
         * @param { number } dy2 - Indicates the y coordinate of the relative target point.
         * @throws { BusinessError } 401 - Parameter error. Possible causes: 1. Mandatory parameters are left unspecified;
         * <br>2. Incorrect parameter types.
         * @syscap SystemCapability.Graphics.Drawing
         * @since 12
         */
        rQuadTo(dx1: number, dy1: number, dx2: number, dy2: number): void;
        /**
         * Draws a conic from the last point of a path to the relative target point.
         * @param { number } ctrlX - Indicates the x coordinate of the relative control point.
         * @param { number } ctrlY - Indicates the y coordinate of the relative control point.
         * @param { number } endX - Indicates the x coordinate of the relative target point.
         * @param { number } endY - Indicates the y coordinate of the relative target point.
         * @param { number } weight - Indicates the weight of added conic.
         * @throws { BusinessError } 401 - Parameter error. Possible causes: 1. Mandatory parameters are left unspecified;
         * <br>2. Incorrect parameter types.
         * @syscap SystemCapability.Graphics.Drawing
         * @since 12
         */
        rConicTo(ctrlX: number, ctrlY: number, endX: number, endY: number, weight: number): void;
        /**
         * Draws a cubic bezier curve from the last point of a path to the relative target point.
         * @param { number } ctrlX1 - Indicates the x coordinate of the first relative control point.
         * @param { number } ctrlY1 - Indicates the y coordinate of the first relative control point.
         * @param { number } ctrlX2 - Indicates the x coordinate of the second relative control point.
         * @param { number } ctrlY2 - Indicates the y coordinate of the second relative control point.
         * @param { number } endX - Indicates the x coordinate of the relative target point.
         * @param { number } endY - Indicates the y coordinate of the relative target point.
         * @throws { BusinessError } 401 - Parameter error. Possible causes: 1. Mandatory parameters are left unspecified;
         * <br>2. Incorrect parameter types.
         * @syscap SystemCapability.Graphics.Drawing
         * @since 12
         */
        rCubicTo(ctrlX1: number, ctrlY1: number, ctrlX2: number, ctrlY2: number, endX: number, endY: number): void;
        /**
         * Adds contour created from point array, adding (count - 1) line segments.
         * @param { Array<common2D.Point> } points - Indicates the point array.
         * @param { boolean } close - Indicates Whether to add lines that connect the end and start.
         * @throws { BusinessError } 401 - Parameter error. Possible causes: 1. Mandatory parameters are left unspecified;
         * <br>2. Incorrect parameter types.
         * @syscap SystemCapability.Graphics.Drawing
         * @since 12
         */
        addPolygon(points: Array<common2D.Point>, close: boolean): void;
        /**
         * Combines two paths.
         * @param { Path } path - Indicates the Path object.
         * @param { PathOp } pathOp - Indicates the operator to apply path.
         * @returns { boolean } boolean - Returns true if constructed path is not empty; returns false otherwise.
         * @throws { BusinessError } 401 - Parameter error. Possible causes: 1. Mandatory parameters are left unspecified;
         * <br>2. Incorrect parameter types; 3. Parameter verification failed.
         * @syscap SystemCapability.Graphics.Drawing
         * @since 12
         */
        op(path: Path, pathOp: PathOp): boolean;
        /**
         * Appends arc to path, as the start of new contour.
         * Arc added is part of ellipse bounded by oval, from startAngle through sweepAngle.
         * @param { common2D.Rect } rect - The bounds of the arc is described by a rect.
         * @param { number } startAngle - Indicates the starting angle of arc in degrees.
         * @param { number } sweepAngle - Indicates the sweep, in degrees. Positive is clockwise.
         * @throws { BusinessError } 401 - Parameter error. Possible causes: 1. Mandatory parameters are left unspecified;
         * <br>2. Incorrect parameter types.
         * @syscap SystemCapability.Graphics.Drawing
         * @since 12
         */
        addArc(rect: common2D.Rect, startAngle: number, sweepAngle: number): void;
        /**
         * Adds a circle to the path, and wound in the specified direction.
         * @param { number } x - Indicates the x coordinate of the center of the circle.
         * @param { number } y - Indicates the y coordinate of the center of the circle.
         * @param { number } radius - Indicates the radius of the circle.
         * @param { PathDirection } pathDirection - The default value is CLOCKWISE.
         * @throws { BusinessError } 401 - Parameter error. Possible causes: 1. Mandatory parameters are left unspecified;
         * <br>2. Incorrect parameter types; 3. Parameter verification failed.
         * @syscap SystemCapability.Graphics.Drawing
         * @since 12
         */
        addCircle(x: number, y: number, radius: number, pathDirection?: PathDirection): void;
        /**
         * Adds a oval to the path, defined by the rect, and wound in the specified direction.
         * @param { common2D.Rect } rect - The bounds of the oval is described by a rect.
         * @param { number } start - Indicates the index of initial point of ellipse.
         * @param { PathDirection } pathDirection - The default value is CLOCKWISE.
         * @throws { BusinessError } 401 - Parameter error. Possible causes: 1. Mandatory parameters are left unspecified;
         * <br>2. Incorrect parameter types; 3. Parameter verification failed.
         * @syscap SystemCapability.Graphics.Drawing
         * @since 12
         */
        addOval(rect: common2D.Rect, start: number, pathDirection?: PathDirection): void;
        /**
         * Adds a new contour to the path, defined by the rect, and wound in the specified direction.
         * @param { common2D.Rect } rect - Indicates the Rect object.
         * @param { PathDirection } pathDirection - The default value is CLOCKWISE.
         * @throws { BusinessError } 401 - Parameter error. Possible causes: 1. Mandatory parameters are left unspecified;
         * <br>2. Incorrect parameter types; 3. Parameter verification failed.
         * @syscap SystemCapability.Graphics.Drawing
         * @since 12
         */
        addRect(rect: common2D.Rect, pathDirection?: PathDirection): void;
        /**
         * Adds a new contour to the path, defined by the round rect, and wound in the specified direction.
         * @param { RoundRect } roundRect - Indicates the RoundRect object.
         * @param { PathDirection } pathDirection - The default value is CLOCKWISE.
         * @throws { BusinessError } 401 - Parameter error. Possible causes: 1. Mandatory parameters are left unspecified;
         * <br>2. Incorrect parameter types; 3. Parameter verification failed.
         * @syscap SystemCapability.Graphics.Drawing
         * @since 12
         */
        addRoundRect(roundRect: RoundRect, pathDirection?: PathDirection): void;
        /**
         * Appends src path to path, transformed by matrix.
         * @param { Path } path - Indicates the Path object.
         * @param { Matrix | null } matrix - Indicates transform applied to path. The default value is null.
         * @throws { BusinessError } 401 - Parameter error. Possible causes: 1. Mandatory parameters are left unspecified;
         * <br>2. Incorrect parameter types.
         * @syscap SystemCapability.Graphics.Drawing
         * @since 12
         */
        addPath(path: Path, matrix?: Matrix | null): void;
        /**
         * Path is replaced by transformed data.
         * @param { Matrix } matrix - Indicates transform applied to path.
         * @throws { BusinessError } 401 - Parameter error. Possible causes: 1. Mandatory parameters are left unspecified;
         * <br>2. Incorrect parameter types.
         * @syscap SystemCapability.Graphics.Drawing
         * @since 12
         */
        transform(matrix: Matrix): void;
        /**
         * Returns the status that point (x, y) is contained by path.
         * @param { number } x - Indicates the x-axis value of containment test.
         * @param { number } y - Indicates the y-axis value of containment test.
         * @returns { boolean } Returns true if the point (x, y) is contained by path; returns false otherwise.
         * @throws { BusinessError } 401 - Parameter error. Possible causes: 1. Mandatory parameters are left unspecified;
         * <br>2. Incorrect parameter types.
         * @syscap SystemCapability.Graphics.Drawing
         * @since 12
         */
        contains(x: number, y: number): boolean;
        /**
         * Sets fill type, the rule used to fill path.
         * @param { PathFillType } pathFillType - Indicates the enum path fill type.
         * @throws { BusinessError } 401 - Parameter error. Possible causes: 1. Mandatory parameters are left unspecified;
         * <br>2. Incorrect parameter types; 3. Parameter verification failed.
         * @syscap SystemCapability.Graphics.Drawing
         * @since 12
         */
        setFillType(pathFillType: PathFillType): void;
        /**
         * Gets the smallest bounding box that contains the path.
         * @returns { common2D.Rect } Rect object.
         * @syscap SystemCapability.Graphics.Drawing
         * @since 12
         */
        getBounds(): common2D.Rect;
        /**
         * Closes a path. A line segment from the start point to the last point of the path is added.
         * @syscap SystemCapability.Graphics.Drawing
         * @since 11
         */
        close(): void;
        /**
         * Offsets point array by (dx, dy). Path is replaced by offset data.
         * @param { number } dx - Indicates offset added to dst path x-axis coordinates.
         * @param { number } dy - Indicates offset added to dst path y-axis coordinates.
         * @returns { Path } Returns a new Path object.
         * @throws { BusinessError } 401 - Parameter error. Possible causes: 1. Mandatory parameters are left unspecified;
         * <br>2. Incorrect parameter types.
         * @syscap SystemCapability.Graphics.Drawing
         * @since 12
         */
        offset(dx: number, dy: number): Path;
        /**
         * Resets path data.
         * @syscap SystemCapability.Graphics.Drawing
         * @since 11
         */
        reset(): void;
        /**
         * Get path length.
         * @param { boolean } forceClosed - Whether to close the Path.
         * @returns { number } Return path length.
         * @syscap SystemCapability.Graphics.Drawing
         * @since 12
         */
        getLength(forceClosed: boolean): number;
        /**
         * Gets the position and tangent of the distance from the starting position of the path.
         *
         * @param { boolean } forceClosed - Whether to close the path.
         * @param { number } distance - The distance from the start of the path, should be greater than 0 and less than 'GetLength()'
         * @param { common2D.Point } position - Sets to the position of distance from the starting position of the path.
         * @param { common2D.Point } tangent - Sets to the tangent of distance from the starting position of the path.
         * @returns { boolean } - Returns true if succeeded, otherwise false.
         * @throws { BusinessError } 401 - Parameter error. Possible causes: 1. Mandatory parameters are left unspecified;
         * <br>2. Incorrect parameter types.
         * @syscap SystemCapability.Graphics.Drawing
         * @since 12
         */
        getPositionAndTangent(forceClosed: boolean, distance: number, position: common2D.Point, tangent: common2D.Point): boolean;
        /**
         * Determines whether the current contour is closed.
         *
         * @returns { boolean } - Returns true if the current contour is closed, otherwise false.
         * @syscap SystemCapability.Graphics.Drawing
         * @since 12
         */
        isClosed(): boolean;
        /**
         * Computes the corresponding matrix at the specified distance.
         *
         * @param { boolean } forceClosed - Whether to close the path.
         * @param { number } distance - The distance from the start of the path.
         * @param { Matrix } matrix - Indicates the pointer to an Matrix object.
         * @param { PathMeasureMatrixFlags } flags - Indicates what should be returned in the matrix.
         * @returns { boolean } - Returns false if there is no path, or a zero-length path was specified, in which case matrix is unchanged.
         * @throws { BusinessError } 401 - Parameter error. Possible causes: Mandatory parameters are left unspecified.
         * @syscap SystemCapability.Graphics.Drawing
         * @since 12
        */
        getMatrix(forceClosed: boolean, distance: number, matrix: Matrix, flags: PathMeasureMatrixFlags): boolean;
        /**
         * Parses the SVG format string that describes the drawing path, and sets the path.
         *
         * @param { string } str - A string in SVG format that describes the drawing path.
         * @returns { boolean } true if build succeeded, otherwise false.
         * @throws { BusinessError } 401 - Parameter error. Possible causes: Mandatory parameters are left unspecified.
         * @syscap SystemCapability.Graphics.Drawing
         * @since 12
         */
        buildFromSvgString(str: string): boolean;
    }
    /**
     * Enumerates of scale to fit flags, selects if an array of points are drawn as discrete points,
     * as lines, or as an open polygon.
     * @enum { number }
     * @syscap SystemCapability.Graphics.Drawing
     * @since 12
     */
    enum PointMode {
        /**
         * Draws each point separately.
         * @syscap SystemCapability.Graphics.Drawing
         * @since 12
         */
        POINTS = 0,
        /**
         * Draws each pair of points as a line segment.
         * @syscap SystemCapability.Graphics.Drawing
         * @since 12
         */
        LINES = 1,
        /**
         * Draws the array of points as a open polygon.
         * @syscap SystemCapability.Graphics.Drawing
         * @since 12
         */
        POLYGON = 2
    }
    /**
     * Enumerates storage filter mode.
     * @enum { number }
     * @syscap SystemCapability.Graphics.Drawing
     * @since 12
     */
    enum FilterMode {
        /**
         * Single sample point (nearest neighbor).
         * @syscap SystemCapability.Graphics.Drawing
         * @since 12
         */
        FILTER_MODE_NEAREST = 0,
        /**
         * Interpolate between 2x2 sample points (bilinear interpolation).
         * @syscap SystemCapability.Graphics.Drawing
         * @since 12
         */
        FILTER_MODE_LINEAR = 1
    }
    /**
     * Enumerates of shadow flags.
     * @enum { number }
     * @syscap SystemCapability.Graphics.Drawing
     * @since 12
     */
    enum ShadowFlag {
        /**
         * Use no shadow flags.
         * @syscap SystemCapability.Graphics.Drawing
         * @since 12
         */
        NONE = 0,
        /**
         * The occluding object is transparent.
         * @syscap SystemCapability.Graphics.Drawing
         * @since 12
         */
        TRANSPARENT_OCCLUDER = 1,
        /**
         * No need to analyze shadows.
         * @syscap SystemCapability.Graphics.Drawing
         * @since 12
         */
        GEOMETRIC_ONLY = 2,
        /**
         * Use all shadow flags.
         * @syscap SystemCapability.Graphics.Drawing
         * @since 12
         */
        ALL = 3
    }
    /**
     * Provides an interface to the drawing, and samplingOptions used when sampling from the image.
     * @syscap SystemCapability.Graphics.Drawing
     * @since 12
     */
    class SamplingOptions {
        /**
         * Constructor for the samplingOptions.
         * @syscap SystemCapability.Graphics.Drawing
         * @since 12
         */
        constructor();
        /**
         * Constructor for the samplingOptions with filter mode.
         * @param { FilterMode } filterMode - Storage filter mode.
         * @throws { BusinessError } 401 - Parameter error. Possible causes: 1. Mandatory parameters are left unspecified;
         * <br>2. Incorrect parameter types.
         * @syscap SystemCapability.Graphics.Drawing
         * @since 12
         */
        constructor(filterMode: FilterMode);
    }
    /**
     * Provides an interface to the drawing, and how to clip and transform the drawing.
     * @syscap SystemCapability.Graphics.Drawing
     * @since 11
     */
    class Canvas {
        /**
         * Constructor for the Canvas.
         * @param { image.PixelMap } pixelmap - PixelMap.
         * @throws { BusinessError } 401 - Parameter error. Possible causes: 1. Mandatory parameters are left unspecified;
         * <br>2. Incorrect parameter types.
         * @syscap SystemCapability.Graphics.Drawing
         * @since 11
         */
        constructor(pixelmap: image.PixelMap);
        /**
         * If rectangle is stroked, use pen to stroke width describes the line thickness,
         * else use brush to fill the rectangle.
         * @param { common2D.Rect } rect - Rectangle to draw.
         * @throws { BusinessError } 401 - Parameter error. Possible causes: 1. Mandatory parameters are left unspecified;
         * <br>2. Incorrect parameter types.
         * @syscap SystemCapability.Graphics.Drawing
         * @since 11
         */
        drawRect(rect: common2D.Rect): void;
        /**
         * If rectangle is stroked, use pen to stroke width describes the line thickness,
         * else use brush to fill the rectangle.
         * @param { number } left - Indicates the left position of the rectangle.
         * @param { number } top - Indicates the top position of the rectangle.
         * @param { number } right - Indicates the right position of the rectangle.
         * @param { number } bottom - Indicates the bottom position of the rectangle.
         * @throws { BusinessError } 401 - Parameter error. Possible causes: 1. Mandatory parameters are left unspecified;
         * <br>2. Incorrect parameter types.
         * @syscap SystemCapability.Graphics.Drawing
         * @since 12
         */
        drawRect(left: number, top: number, right: number, bottom: number): void;
        /**
         * Draws a RoundRect.
         * @param { RoundRect } roundRect - Indicates the RectRound object.
         * @throws { BusinessError } 401 - Parameter error. Possible causes: 1. Mandatory parameters are left unspecified;
         * <br>2. Incorrect parameter types.
         * @syscap SystemCapability.Graphics.Drawing
         * @since 12
         */
        drawRoundRect(roundRect: RoundRect): void;
        /**
         * Draws a nested RoundRect.
         * @param { RoundRect } outer - Indicates the outer RectRound object.
         * @param { RoundRect } inner - Indicates the inner RectRound object.
         * @throws { BusinessError } 401 - Parameter error. Possible causes: 1. Mandatory parameters are left unspecified;
         * <br>2. Incorrect parameter types.
         * @syscap SystemCapability.Graphics.Drawing
         * @since 12
         */
        drawNestedRoundRect(outer: RoundRect, inner: RoundRect): void;
        /**
         * Fills clipped canvas area with brush.
         * @param { Brush } brush - Indicates the Brush object.
         * @throws { BusinessError } 401 - Parameter error. Possible causes: 1. Mandatory parameters are left unspecified;
         * <br>2. Incorrect parameter types.
         * @syscap SystemCapability.Graphics.Drawing
         * @since 12
         */
        drawBackground(brush: Brush): void;
        /**
         * Draws an offset spot shadow and outlining ambient shadow for the given path with circular light.
         * @param { Path } path - Indicates the Path object.
         * @param { common2D.Point3d } planeParams - Represents z offset of the occluder from the canvas based on x and y.
         * @param { common2D.Point3d } devLightPos - Represents the position of the light relative to the canvas.
         * @param { number } lightRadius - The radius of the circular light.
         * @param { common2D.Color } ambientColor - Ambient shadow's color.
         * @param { common2D.Color } spotColor - Spot shadow's color.
         * @param { ShadowFlag } flag - Indicates the flag to control opaque occluder, shadow, and light position.
         * @throws { BusinessError } 401 - Parameter error. Possible causes: 1. Mandatory parameters are left unspecified;
         * <br>2. Incorrect parameter types; 3. Parameter verification failed.
         * @syscap SystemCapability.Graphics.Drawing
         * @since 12
         */
        drawShadow(path: Path, planeParams: common2D.Point3d, devLightPos: common2D.Point3d, lightRadius: number, ambientColor: common2D.Color, spotColor: common2D.Color, flag: ShadowFlag): void;
        /**
         * If radius is zero or less, nothing is drawn. If circle is stroked, use pen to
         * stroke width describes the line thickness, else use brush to fill the circle.
         * @param { number } x - X coordinate of the circle center.
         * @param { number } y - Y coordinate of the circle center.
         * @param { number } radius - The radius of the circle must be greater than 0.
         * @throws { BusinessError } 401 - Parameter error. Possible causes: 1. Mandatory parameters are left unspecified;
         * <br>2. Incorrect parameter types; 3. Parameter verification failed.
         * @syscap SystemCapability.Graphics.Drawing
         * @since 11
         */
        drawCircle(x: number, y: number, radius: number): void;
        /**
         * Draw a pixelmap, with the upper left corner at (left, top).
         * @param { image.PixelMap } pixelmap - PixelMap.
         * @param { number } left - Left side of image.
         * @param { number } top - Top side of image.
         * @throws { BusinessError } 401 - Parameter error.
         * @syscap SystemCapability.Graphics.Drawing
         * @since 11
         */
        /**
         * Draw a pixelmap, with the upper left corner at (left, top).
         * @param { image.PixelMap } pixelmap - PixelMap.
         * @param { number } left - Left side of image.
         * @param { number } top - Top side of image.
         * @param { SamplingOptions } samplingOptions - SamplingOptions used to describe the sampling mode.
         * @throws { BusinessError } 401 - Parameter error. Possible causes: 1. Mandatory parameters are left unspecified;
         * <br>2. Incorrect parameter types.
         * @syscap SystemCapability.Graphics.Drawing
         * @since 12
         */
        drawImage(pixelmap: image.PixelMap, left: number, top: number, samplingOptions?: SamplingOptions): void;
        /**
         * Draws the specified source image onto the canvas,
         * scaled and translated to the destination rectangle.
         * @param { image.PixelMap } pixelmap - The source image.
         * @param { common2D.Rect } dstRect - The area of destination canvas.
         * @param { SamplingOptions } samplingOptions - SamplingOptions used to describe the sampling mode.
         * @throws { BusinessError } 401 - Parameter error. Possible causes: 1. Mandatory parameters are left unspecified;
         * <br>2. Incorrect parameter types.
         * @syscap SystemCapability.Graphics.Drawing
         * @since 12
         */
        drawImageRect(pixelmap: image.PixelMap, dstRect: common2D.Rect, samplingOptions?: SamplingOptions): void;
        /**
         * Draws the specified source rectangle of the image onto the canvas,
         * scaled and translated to the destination rectangle.
         * @param { image.PixelMap } pixelmap - The source image.
         * @param { common2D.Rect } srcRect - The area of source image.
         * @param { common2D.Rect } dstRect - The area of destination canvas.
         * @param { SamplingOptions } samplingOptions - SamplingOptions used to describe the sampling mode.
         * @param { SrcRectConstraint } constraint - Constraint type. The default value is STRICT.
         * @throws { BusinessError } 401 - Parameter error. Possible causes: 1. Mandatory parameters are left unspecified;
         * <br>2. Incorrect parameter types.
         * @syscap SystemCapability.Graphics.Drawing
         * @since 12
         */
        drawImageRectWithSrc(pixelmap: image.PixelMap, srcRect: common2D.Rect, dstRect: common2D.Rect, samplingOptions?: SamplingOptions, constraint?: SrcRectConstraint): void;
        /**
         * Fills clip with color color. Mode determines how ARGB is combined with destination.
         * @param { common2D.Color } color - The range of color channels must be [0, 255].
         * @param { BlendMode } blendMode - Used to combine source color and destination. The default value is SRC_OVER.
         * @throws { BusinessError } 401 - Parameter error. Possible causes: 1. Mandatory parameters are left unspecified;
         * <br>2. Incorrect parameter types; 3. Parameter verification failed.
         * @syscap SystemCapability.Graphics.Drawing
         * @since 11
         */
        drawColor(color: common2D.Color, blendMode?: BlendMode): void;
        /**
         * Fills the clipped rectangle with the specified ARGB color.
         * @param { number } alpha - Alpha channel of color. The range of alpha must be [0, 255].
         * @param { number } red - Red channel of color. The range of red must be [0, 255].
         * @param { number } green - Green channel of color. The range of green must be [0, 255].
         * @param { number } blue - Blue channel of color. The range of blue must be [0, 255].
         * @param { BlendMode } blendMode - Used to combine source color and destination. The default value is SRC_OVER.
         * @throws { BusinessError } 401 - Parameter error. Possible causes: 1. Mandatory parameters are left unspecified;
         * <br>2. Incorrect parameter types; 3. Parameter verification failed.
         * @syscap SystemCapability.Graphics.Drawing
         * @since 12
         */
        drawColor(alpha: number, red: number, green: number, blue: number, blendMode?: BlendMode): void;
        /**
         * Draws an oval.
         * @param { common2D.Rect } oval - The bounds of the oval is described by a rect.
         * @throws { BusinessError } 401 - Parameter error. Possible causes: 1. Mandatory parameters are left unspecified;
         * <br>2. Incorrect parameter types.
         * @syscap SystemCapability.Graphics.Drawing
         * @since 12
         */
        drawOval(oval: common2D.Rect): void;
        /**
         * Draws an arc.
         * @param { common2D.Rect } arc - The bounds of the arc is described by a rect.
         * @param { number } startAngle - Indicates the startAngle of the arc.
         * @param { number } sweepAngle - Indicates the sweepAngle of the arc.
         * @throws { BusinessError } 401 - Parameter error. Possible causes: 1. Mandatory parameters are left unspecified;
         * <br>2. Incorrect parameter types.
         * @syscap SystemCapability.Graphics.Drawing
         * @since 12
         */
        drawArc(arc: common2D.Rect, startAngle: number, sweepAngle: number): void;
        /**
         * Draw a point.
         * @param { number } x - X coordinate position of the point.
         * @param { number } y - Y coordinate position of the point.
         * @throws { BusinessError } 401 - Parameter error. Possible causes: 1. Mandatory parameters are left unspecified;
         * <br>2. Incorrect parameter types.
         * @syscap SystemCapability.Graphics.Drawing
         * @since 11
         */
        drawPoint(x: number, y: number): void;
        /**
         * Draws point array as separate point, line segment or open polygon according to given point mode.
         * @param { Array<common2D.Point> } points - Points array.
         * @param { PointMode } mode - Draws points enum method. The default value is POINTS.
         * @throws { BusinessError } 401 - Parameter error. Possible causes: 1. Mandatory parameters are left unspecified;
         * <br>2. Incorrect parameter types; 3. Parameter verification failed.
         * @syscap SystemCapability.Graphics.Drawing
         * @since 12
         */
        drawPoints(points: Array<common2D.Point>, mode?: PointMode): void;
        /**
         * Draws a path.
         * @param { Path } path - Path to draw.
         * @throws { BusinessError } 401 - Parameter error. Possible causes: 1. Mandatory parameters are left unspecified;
         * <br>2. Incorrect parameter types.
         * @syscap SystemCapability.Graphics.Drawing
         * @since 11
         */
        drawPath(path: Path): void;
        /**
         * Draws line segment from startPt to endPt.
         * @param { number } x0 - X coordinate of the start point of the line segment.
         * @param { number } y0 - Y coordinate of the start point of the line segment.
         * @param { number } x1 - X coordinate of the end point of the line segment.
         * @param { number } y1 - Y coordinate of the end point of the line segment.
         * @throws { BusinessError } 401 - Parameter error. Possible causes: 1. Mandatory parameters are left unspecified;
         * <br>2. Incorrect parameter types.
         * @syscap SystemCapability.Graphics.Drawing
         * @since 11
         */
        drawLine(x0: number, y0: number, x1: number, y1: number): void;
        /**
         * Draws a single character.
         * @param { string } text - A string containing only a single character.
         * @param { Font } font - Font object.
         * @param { number } x - X coordinate of the single character start point.
         * @param { number } y - Y coordinate of the single character start point.
         * @throws { BusinessError } 401 - Parameter error. Possible causes: 1. Mandatory parameters are left unspecified;
         * <br>2. Incorrect parameter types; 3. Parameter verification failed.
         * @syscap SystemCapability.Graphics.Drawing
         * @since 12
         */
        drawSingleCharacter(text: string, font: Font, x: number, y: number): void;
        /**
         * Draws a textBlob
         * @param { TextBlob } blob - TextBlob to draw.
         * @param { number } x - X coordinate of the text start point.
         * @param { number } y - Y coordinate of the text start point.
         * @throws { BusinessError } 401 - Parameter error. Possible causes: 1. Mandatory parameters are left unspecified;
         * <br>2. Incorrect parameter types.
         * @syscap SystemCapability.Graphics.Drawing
         * @since 11
         */
        drawTextBlob(blob: TextBlob, x: number, y: number): void;
        /**
         * Draws the pixelmap base on the mesh which is evenly distributed across the pixelmap.
         * @param { image.PixelMap } pixelmap - The pixelmap to draw using the mesh.
         * @param { number } meshWidth - The number of columns in the mesh.
         * @param { number } meshHeight - The number of rows in the mesh.
         * @param { Array<number> } vertices - Array of vertices, specifying where the mesh should be drawn.
         * @param { number } vertOffset - Number of vert elements to skip before drawing.
         * @param { Array<number> } colors - Array of colors, specifying a color at each vertex.
         * @param { number } colorOffset - Number of color elements to skip before drawing.
         * @throws { BusinessError } 401 - Parameter error. Possible causes: 1. Mandatory parameters are left unspecified;
         * <br>2. Incorrect parameter types.
         * @syscap SystemCapability.Graphics.Drawing
         * @since 12
         */
        drawPixelMapMesh(pixelmap: image.PixelMap, meshWidth: number, meshHeight: number, vertices: Array<number>, vertOffset: number, colors: Array<number>, colorOffset: number): void;
        /**
         * Draws a region.
         * @param { Region } region - Region object.
         * @throws { BusinessError } 401 - Parameter error. Possible causes: 1. Mandatory parameters are left unspecified;
         * <br>2. Incorrect parameter types.
         * @syscap SystemCapability.Graphics.Drawing
         * @since 12
         */
        drawRegion(region: Region): void;
        /**
         * Set pen to a canvas.
         * @param { Pen } pen - object.
         * @throws { BusinessError } 401 - Parameter error. Possible causes: 1. Mandatory parameters are left unspecified;
         * <br>2. Incorrect parameter types.
         * @syscap SystemCapability.Graphics.Drawing
         * @since 11
         */
        attachPen(pen: Pen): void;
        /**
         * Set brush to a canvas.
         * @param { Brush } brush - Object.
         * @throws { BusinessError } 401 - Parameter error. Possible causes: 1. Mandatory parameters are left unspecified;
         * <br>2. Incorrect parameter types.
         * @syscap SystemCapability.Graphics.Drawing
         * @since 11
         */
        attachBrush(brush: Brush): void;
        /**
         * Unset pen to a canvas.
         * @syscap SystemCapability.Graphics.Drawing
         * @since 11
         */
        detachPen(): void;
        /**
         * Unset brush to a canvas.
         * @syscap SystemCapability.Graphics.Drawing
         * @since 11
         */
        detachBrush(): void;
        /**
         * Saves the current canvas status (canvas matrix) to the top of the stack.
         * @returns { number } Return the number of saved states.
         * @syscap SystemCapability.Graphics.Drawing
         * @since 12
         */
        save(): number;
        /**
         * Saves matrix and clip, and allocates a bitmap for subsequent drawing.
         * Calling restore discards changes to matrix and clip, and draws the bitmap.
         * @param { common2D.Rect | null} rect - Optional layer size. The default value is null.
         * @param { Brush | null} brush - Optional brush effect used to draw the layer. The default value is null.
         * @returns { number } Return the number of saved states before this call.
         * @throws { BusinessError } 401 - Parameter error. Possible causes: Mandatory parameters are left unspecified.
         * @syscap SystemCapability.Graphics.Drawing
         * @since 12
         */
        saveLayer(rect?: common2D.Rect | null, brush?: Brush | null): number;
        /**
         * Clears a canvas by using a specified color.
         * @param { common2D.Color } color - Indicates the color, which is a 32-bit (ARGB) variable.
         * @throws { BusinessError } 401 - Parameter error. Possible causes: 1. Mandatory parameters are left unspecified;
         * <br>2. Incorrect parameter types.
         * @syscap SystemCapability.Graphics.Drawing
         * @since 12
         */
        clear(color: common2D.Color): void;
        /**
         * Restores the canvas status (canvas matrix) saved on the top of the stack.
         * @syscap SystemCapability.Graphics.Drawing
         * @since 12
         */
        restore(): void;
        /**
         * Restores the specific number of the canvas status (canvas matrix) saved in the stack.
         * @param { number } count - Depth of state stack to restore.
         * @throws { BusinessError } 401 - Parameter error. Possible causes: 1. Mandatory parameters are left unspecified;
         * <br>2. Incorrect parameter types.
         * @syscap SystemCapability.Graphics.Drawing
         * @since 12
         */
        restoreToCount(count: number): void;
        /**
         * Gets the number of the canvas status (canvas matrix) saved in the stack.
         * @returns { number } Return represent depth of save state stack.
         * @syscap SystemCapability.Graphics.Drawing
         * @since 12
         */
        getSaveCount(): number;
        /**
         * Gets the width of a canvas.
         * @returns { number } Return the width of a canvas.
         * @syscap SystemCapability.Graphics.Drawing
         * @since 12
         */
        getWidth(): number;
        /**
         * Gets the height of a canvas.
         * @returns { number } Return the height of a canvas.
         * @syscap SystemCapability.Graphics.Drawing
         * @since 12
         */
        getHeight(): number;
        /**
         * Gets the bounds of clip of a canvas.
         * @returns { common2D.Rect } Rect object.
         * @syscap SystemCapability.Graphics.Drawing
         * @since 12
         */
        getLocalClipBounds(): common2D.Rect;
        /**
         * Gets a 3x3 matrix of the transform from local coordinates to 'device'.
         * @returns { Matrix } Matrix object.
         * @syscap SystemCapability.Graphics.Drawing
         * @since 12
         */
        getTotalMatrix(): Matrix;
        /**
         * Scales by sx on the x-axis and sy on the y-axis.
         * @param { number } sx - Indicates the amount to scale on x-axis.
         * @param { number } sy - Indicates the amount to scale on y-axis.
         * @throws { BusinessError } 401 - Parameter error. Possible causes: 1. Mandatory parameters are left unspecified;
         * <br>2. Incorrect parameter types.
         * @syscap SystemCapability.Graphics.Drawing
         * @since 12
         */
        scale(sx: number, sy: number): void;
        /**
         * Skews by sx on the x-axis and sy on the y-axis.
         * @param { number } sx - Indicates the value skew transformation on x-axis.
         * @param { number } sy - Indicates the value skew transformation on y-axis.
         * @throws { BusinessError } 401 - Parameter error. Possible causes: 1. Mandatory parameters are left unspecified;
         * <br>2. Incorrect parameter types.
         * @syscap SystemCapability.Graphics.Drawing
         * @since 12
         */
        skew(sx: number, sy: number): void;
        /**
         * Rotates by degrees, positive degrees rotates clockwise.
         * @param { number } degrees - Indicates the amount to rotate, in degrees.
         * @param { number } sx - Indicates the x-axis value of the point to rotate about.
         * @param { number } sy - Indicates the y-axis value of the point to rotate about.
         * @throws { BusinessError } 401 - Parameter error. Possible causes: 1. Mandatory parameters are left unspecified;
         * <br>2. Incorrect parameter types.
         * @syscap SystemCapability.Graphics.Drawing
         * @since 12
         */
        rotate(degrees: number, sx: number, sy: number): void;
        /**
         * Translates by dx along the x-axis and dy along the y-axis.
         * @param { number } dx - Indicates the distance to translate on x-axis.
         * @param { number } dy - Indicates the distance to translate on y-axis.
         * @throws { BusinessError } 401 - Parameter error. Possible causes: 1. Mandatory parameters are left unspecified;
         * <br>2. Incorrect parameter types.
         * @syscap SystemCapability.Graphics.Drawing
         * @since 12
         */
        translate(dx: number, dy: number): void;
        /**
         * Replaces the clipping area with the intersection or difference of the current clipping area and path,
         * and use a clipping edge that is aliased or anti-aliased.
         * @param { Path } path - To combine with clip.
         * @param { ClipOp } clipOp - Indicates the operation to apply to clip. The default value is intersect.
         * @param { boolean } doAntiAlias - True if clip is to be anti-aliased. The default value is false.
         * @throws { BusinessError } 401 - Parameter error. Possible causes: 1. Mandatory parameters are left unspecified;
         * <br>2. Incorrect parameter types.
         * @syscap SystemCapability.Graphics.Drawing
         * @since 12
         */
        clipPath(path: Path, clipOp?: ClipOp, doAntiAlias?: boolean): void;
        /**
         * Replaces the clipping area with the intersection or difference between the
         * current clipping area and Rect, and use a clipping edge that is aliased or anti-aliased.
         * @param { common2D.Rect } rect - To combine with clipping area.
         * @param { ClipOp } clipOp - Indicates the operation to apply to clip. The default value is intersect.
         * @param { boolean } doAntiAlias - True if clip is to be anti-aliased. The default value is false.
         * @throws { BusinessError } 401 - Parameter error. Possible causes: 1. Mandatory parameters are left unspecified;
         * <br>2. Incorrect parameter types.
         * @syscap SystemCapability.Graphics.Drawing
         * @since 12
         */
        clipRect(rect: common2D.Rect, clipOp?: ClipOp, doAntiAlias?: boolean): void;
        /**
         * Uses the passed matrix to transforming the geometry, then use existing matrix.
         * @param { Matrix } matrix - Declares functions related to the matrix object in the drawing module.
         * @throws { BusinessError } 401 - Parameter error. Possible causes: 1. Mandatory parameters are left unspecified;
         * <br>2. Incorrect parameter types.
         * @syscap SystemCapability.Graphics.Drawing
         * @since 12
         */
        concatMatrix(matrix: Matrix): void;
        /**
         * Replace the clipping area with the intersection or difference of the
         * current clipping area and Region, and use a clipping edge that is aliased or anti-aliased.
         * @param { Region } region - Region object.
         * @param { ClipOp } clipOp - Indicates the operation to apply to clip. The default value is intersect.
         * @throws { BusinessError } 401 - Parameter error. Possible causes: 1. Mandatory parameters are left unspecified;
         * <br>2. Incorrect parameter types.
         * @syscap SystemCapability.Graphics.Drawing
         * @since 12
         */
        clipRegion(region: Region, clipOp?: ClipOp): void;
        /**
         * Replaces the clipping area with the intersection or difference between the
         * current clipping area and RoundRect, and use a clipping edge that is aliased or anti-aliased.
         * @param { RoundRect } roundRect - To combine with clipping area.
         * @param { ClipOp } clipOp - Indicates the operation to apply to clip. The default value is intersect.
         * @param { boolean } doAntiAlias - True if clip is to be anti-aliased. The default value is false.
         * @throws { BusinessError } 401 - Parameter error. Possible causes: 1. Mandatory parameters are left unspecified;
         * <br>2. Incorrect parameter types.
         * @syscap SystemCapability.Graphics.Drawing
         * @since 12
         */
        clipRoundRect(roundRect: RoundRect, clipOp?: ClipOp, doAntiAlias?: boolean): void;
        /**
         * Checks whether the drawable area is empty.
         * @returns { boolean } Returns true if drawable area is empty.
         * @syscap SystemCapability.Graphics.Drawing
         * @since 12
         */
        isClipEmpty(): boolean;
        /**
         * Sets matrix of canvas.
         * @param { Matrix } matrix - Declares functions related to the matrix object in the drawing module.
         * @throws { BusinessError } 401 - Parameter error. Possible causes: 1. Mandatory parameters are left unspecified;
         * <br>2. Incorrect parameter types.
         * @syscap SystemCapability.Graphics.Drawing
         * @since 12
         */
        setMatrix(matrix: Matrix): void;
        /**
         * Sets matrix of canvas to the identity matrix.
         * @syscap SystemCapability.Graphics.Drawing
         * @since 12
         */
        resetMatrix(): void;
    }
    /**
     * Enumerates clip operations.
     *
     * @enum { number }
     * @syscap SystemCapability.Graphics.Drawing
     * @since 12
     */
    enum ClipOp {
        /**
         * Clips with difference.
         * @syscap SystemCapability.Graphics.Drawing
         * @since 12
         */
        DIFFERENCE = 0,
        /**
         * Clips with intersection.
         * @syscap SystemCapability.Graphics.Drawing
         * @since 12
         */
        INTERSECT = 1
    }
    /**
     * Provide a description of the type and position of the text.
     * @typedef TextBlobRunBuffer
     * @syscap SystemCapability.Graphics.Drawing
     * @since 11
     */
    interface TextBlobRunBuffer {
        /**
         * Text model.
         * @type { number }
         * @syscap SystemCapability.Graphics.Drawing
         * @since 11
         */
        glyph: number;
        /**
         * X-coordinate of the text start point.
         * @type { number }
         * @syscap SystemCapability.Graphics.Drawing
         * @since 11
         */
        positionX: number;
        /**
         * Y-coordinate of the text start point.
         * @type { number }
         * @syscap SystemCapability.Graphics.Drawing
         * @since 11
         */
        positionY: number;
    }
    /**
     * Encoding type of the description text.
     *
     * @enum { number }
     * @syscap SystemCapability.Graphics.Drawing
     * @since 11
     */
    enum TextEncoding {
        /**
         * Use 1 byte to represent UTF-8 or ASCII
         * @syscap SystemCapability.Graphics.Drawing
         * @since 11
         */
        TEXT_ENCODING_UTF8 = 0,
        /**
         * Use 2 bytes to represent most of unicode
         * @syscap SystemCapability.Graphics.Drawing
         * @since 11
         */
        TEXT_ENCODING_UTF16 = 1,
        /**
         * Use 4 bytes to represent all unicode.
         * @syscap SystemCapability.Graphics.Drawing
         * @since 11
         */
        TEXT_ENCODING_UTF32 = 2,
        /**
         * Use 2 bytes to represent the glyph index.
         * @syscap SystemCapability.Graphics.Drawing
         * @since 11
         */
        TEXT_ENCODING_GLYPH_ID = 3
    }
    /**
     * Provide a description of the text
     *
     * class TextBlob
     * @syscap SystemCapability.Graphics.Drawing
     * @since 11
     */
    class TextBlob {
        /**
         * Create a textblob from a string
         * @param { string } text - Drawn glyph content.
         * @param { Font } font - Specify text size, font, text scale, etc.
         * @param { TextEncoding } encoding - The default value is TEXT_ENCODING_UTF8.
         * @returns { TextBlob } TextBlob object.
         * @throws { BusinessError } 401 - Parameter error. Possible causes: 1. Mandatory parameters are left unspecified;
         * <br>2. Incorrect parameter types.
         * @static
         * @syscap SystemCapability.Graphics.Drawing
         * @since 11
         */
        static makeFromString(text: string, font: Font, encoding?: TextEncoding): TextBlob;
        /**
         * Create a textblob from a string, each element of which is located at the given positions.
         * @param { string } text - Drawn glyph content.
         * @param { number } len - string length, value must equal to points length.
         * @param { common2D.Point[] } points - Position coordinates of a textblob elements.
         * @param { Font } font - Specify text size, font, text scale, etc.
         * @returns { TextBlob } TextBlob object.
         * @throws { BusinessError } 401 - Parameter error. Possible causes: 1. Mandatory parameters are left unspecified;
         * <br>2. Incorrect parameter types.
         * @static
         * @syscap SystemCapability.Graphics.Drawing
         * @since 12
         */
        static makeFromPosText(text: string, len: number, points: common2D.Point[], font: Font): TextBlob;
        /**
         * Creating a textblob object based on RunBuffer information
         * @param { Array<TextBlobRunBuffer> } pos - The array of TextBlobRunBuffer.
         * @param { Font } font - Font used for this run.
         * @param { common2D.Rect } bounds - Optional run bounding box. The default value is null;
         * @returns { TextBlob } TextBlob object.
         * @throws { BusinessError } 401 - Parameter error. Possible causes: 1. Mandatory parameters are left unspecified;
         * <br>2. Incorrect parameter types.
         * @static
         * @syscap SystemCapability.Graphics.Drawing
         * @since 11
         */
        static makeFromRunBuffer(pos: Array<TextBlobRunBuffer>, font: Font, bounds?: common2D.Rect): TextBlob;
        /**
         * Returns the bounding rectangle shape
         * @returns { common2D.Rect } Rect object.
         * @syscap SystemCapability.Graphics.Drawing
         * @since 11
         */
        bounds(): common2D.Rect;
        /**
         * Returns an unique identifier for a textblob.
         * @returns { number } Unique ID.
         * @syscap SystemCapability.Graphics.Drawing
         * @since 12
         */
        uniqueID(): number;
    }
    /**
     * The Typeface class specifies the typeface and intrinsic style of a font.
     *
     * @syscap SystemCapability.Graphics.Drawing
     * @since 11
     */
    class Typeface {
        /**
         * Get the family name for this typeface.
         * @returns { string } Family name.
         * @syscap SystemCapability.Graphics.Drawing
         * @since 11
         */
        getFamilyName(): string;
        /**
         * Generate typeface from file.
         * @param { string } filePath - file path for typeface.
         * @returns { Typeface } Typeface.
         * @throws { BusinessError } 401 - Parameter error. Possible causes: 1. Mandatory parameters are left unspecified;
         * <br>2. Incorrect parameter types.
         * @syscap SystemCapability.Graphics.Drawing
         * @since 12
         */
        static makeFromFile(filePath: string): Typeface;
    }
    /**
     * Enumerates text edging types.
     *
     * @enum { number }
     * @syscap SystemCapability.Graphics.Drawing
     * @since 12
     */
    enum FontEdging {
        /**
         * Uses anti aliasing, default value.
         * @syscap SystemCapability.Graphics.Drawing
         * @since 12
         */
        ALIAS = 0,
        /**
         * Uses sub-pixel anti aliasing.
         * @syscap SystemCapability.Graphics.Drawing
         * @since 12
         */
        ANTI_ALIAS = 1,
        /**
         * Uses sub-pixel anti aliasing and enable sub-pixel localization.
         * @syscap SystemCapability.Graphics.Drawing
         * @since 12
         */
        SUBPIXEL_ANTI_ALIAS = 2
    }
    /**
     * Enumerates text hinting types.
     *
     * @enum { number }
     * @syscap SystemCapability.Graphics.Drawing
     * @since 12
     */
    enum FontHinting {
        /**
         * Not use text hinting.
         * @syscap SystemCapability.Graphics.Drawing
         * @since 12
         */
        NONE = 0,
        /**
         * Uses slight text hinting.
         * @syscap SystemCapability.Graphics.Drawing
         * @since 12
         */
        SLIGHT = 1,
        /**
         * Uses normal text hinting.
         * @syscap SystemCapability.Graphics.Drawing
         * @since 12
         */
        NORMAL = 2,
        /**
         * Uses full text hinting.
         * @syscap SystemCapability.Graphics.Drawing
         * @since 12
         */
        FULL = 3
    }
    /**
     * Font controls options applied when drawing and measuring text.
     *
     * @syscap SystemCapability.Graphics.Drawing
     * @since 11
     */
    class Font {
        /**
         * Requests, but does not require, that glyphs respect sub-pixel positioning.
         * @param { boolean } isSubpixel - Setting for sub-pixel positioning.
         * @throws { BusinessError } 401 - Parameter error. Possible causes: 1. Mandatory parameters are left unspecified;
         * <br>2. Incorrect parameter types.
         * @syscap SystemCapability.Graphics.Drawing
         * @since 11
         */
        enableSubpixel(isSubpixel: boolean): void;
        /**
         * Increases stroke width when creating glyph bitmaps to approximate a bold typeface.
         * @param { boolean } isEmbolden - Setting for bold approximation.
         * @throws { BusinessError } 401 - Parameter error. Possible causes: 1. Mandatory parameters are left unspecified;
         * <br>2. Incorrect parameter types.
         * @syscap SystemCapability.Graphics.Drawing
         * @since 11
         */
        enableEmbolden(isEmbolden: boolean): void;
        /**
         * Requests linearly scalable font and glyph metrics.
         * @param { boolean } isLinearMetrics - Setting for linearly scalable font and glyph metrics.
         * @throws { BusinessError } 401 - Parameter error. Possible causes: 1. Mandatory parameters are left unspecified;
         * <br>2. Incorrect parameter types.
         * @syscap SystemCapability.Graphics.Drawing
         * @since 11
         */
        enableLinearMetrics(isLinearMetrics: boolean): void;
        /**
         * Sets text size in points. Has no effect if textSize is not greater than or equal to zero.
         * @param { number } textSize - Typographic height of text. The height of the text must be greater than 0.
         * @throws { BusinessError } 401 - Parameter error. Possible causes: 1. Mandatory parameters are left unspecified;
         * <br>2. Incorrect parameter types; 3. Parameter verification failed.
         * @syscap SystemCapability.Graphics.Drawing
         * @since 11
         */
        setSize(textSize: number): void;
        /**
         * Obtains the text size.
         * @returns { number } Text size.
         * @syscap SystemCapability.Graphics.Drawing
         * @since 11
         */
        getSize(): number;
        /**
         * Sets Typeface to font.
         * @param { Typeface } typeface - Font and style used to draw text.
         * @throws { BusinessError } 401 - Parameter error. Possible causes: 1. Mandatory parameters are left unspecified;
         * <br>2. Incorrect parameter types.
         * @syscap SystemCapability.Graphics.Drawing
         * @since 11
         */
        setTypeface(typeface: Typeface): void;
        /**
         * Get Typeface to font.
         * @returns { Typeface } Typeface.
         * @syscap SystemCapability.Graphics.Drawing
         * @since 11
         */
        getTypeface(): Typeface;
        /**
         * Get fontMetrics associated with typeface.
         * @returns { FontMetrics } The fontMetrics value returned to the caller.
         * @syscap SystemCapability.Graphics.Drawing
         * @since 11
         */
        getMetrics(): FontMetrics;
        /**
         * Measure a single character.
         * @param { string } text - A string containing only a single character.
         * @returns { number } The width of the single character.
         * @throws { BusinessError } 401 - Parameter error. Possible causes: 1. Mandatory parameters are left unspecified;
         * <br>2. Incorrect parameter types; 3. Parameter verification failed.
         * @syscap SystemCapability.Graphics.Drawing
         * @since 12
         */
        measureSingleCharacter(text: string): number;
        /**
         * Measure the width of text.
         * @param { string } text - Text Symbol Content.
         * @param { TextEncoding } encoding - Encoding format.
         * @returns { number } The width of text.
         * @throws { BusinessError } 401 - Parameter error. Possible causes: 1. Mandatory parameters are left unspecified;
         * <br>2. Incorrect parameter types.
         * @syscap SystemCapability.Graphics.Drawing
         * @since 11
         */
        measureText(text: string, encoding: TextEncoding): number;
        /**
         * Sets text scale on x-axis to font.
         * @param { number } scaleX - Text scaleX.
         * @throws { BusinessError } 401 - Parameter error. Possible causes: 1. Mandatory parameters are left unspecified;
         * <br>2. Incorrect parameter types.
         * @syscap SystemCapability.Graphics.Drawing
         * @since 12
         */
        setScaleX(scaleX: number): void;
        /**
         * Sets text skew on x-axis to font.
         * @param { number } skewX - Text skewX.
         * @throws { BusinessError } 401 - Parameter error. Possible causes: 1. Mandatory parameters are left unspecified;
         * <br>2. Incorrect parameter types.
         * @syscap SystemCapability.Graphics.Drawing
         * @since 12
         */
        setSkewX(skewX: number): void;
        /**
         * Sets the edging effect to font.
         * @param { FontEdging } edging - Text edging.
         * @throws { BusinessError } 401 - Parameter error. Possible causes: 1. Mandatory parameters are left unspecified;
         * <br>2. Incorrect parameter types; 3. Parameter verification failed.
         * @syscap SystemCapability.Graphics.Drawing
         * @since 12
         */
        setEdging(edging: FontEdging): void;
        /**
         * Sets the hinting pattern to font.
         * @param { FontHinting } hinting - Text hinting.
         * @throws { BusinessError } 401 - Parameter error. Possible causes: 1. Mandatory parameters are left unspecified;
         * <br>2. Incorrect parameter types; 3. Parameter verification failed.
         * @syscap SystemCapability.Graphics.Drawing
         * @since 12
         */
        setHinting(hinting: FontHinting): void;
        /**
         * Calculates number of glyphs represented by text.
         * @param { string } text - Indicates the character storage encoded with text encoding.
         * @returns { number } Returns the count of text.
         * @throws { BusinessError } 401 - Parameter error. Possible causes: 1. Mandatory parameters are left unspecified;
         * <br>2. Incorrect parameter types.
         * @syscap SystemCapability.Graphics.Drawing
         * @since 12
         */
        countText(text: string): number;
        /**
         * Sets whether the font baselines and pixels alignment when the transformation matrix is ​​axis aligned.
         * @param { boolean } isBaselineSnap - Indicates whether the font baselines and pixels alignment.
         * @throws { BusinessError } 401 - Parameter error. Possible causes: 1. Mandatory parameters are left unspecified;
         * <br>2. Incorrect parameter types.
         * @syscap SystemCapability.Graphics.Drawing
         * @since 12
         */
        setBaselineSnap(isBaselineSnap: boolean): void;
        /**
         * Gets whether the font baselines and pixels alignment when the transformation matrix is ​​axis aligned.
         * @returns { boolean } Returns true if the font baselines and pixels alignment; returns false otherwise.
         * @syscap SystemCapability.Graphics.Drawing
         * @since 12
         */
        isBaselineSnap(): boolean;
        /**
         * Sets whether to use bitmaps instead of outlines in the object.
         * @param { boolean } isEmbeddedBitmaps - Indicates whether to use bitmaps instead of outlines.
         * @throws { BusinessError } 401 - Parameter error. Possible causes: 1. Mandatory parameters are left unspecified;
         * <br>2. Incorrect parameter types.
         * @syscap SystemCapability.Graphics.Drawing
         * @since 12
         */
        setEmbeddedBitmaps(isEmbeddedBitmaps: boolean): void;
        /**
         * Gets whether to use bitmaps instead of outlines in the object.
         * @returns { boolean } if using bitmaps instead of outlines; returns false otherwise.
         * @syscap SystemCapability.Graphics.Drawing
         * @since 12
         */
        isEmbeddedBitmaps(): boolean;
        /**
         * Sets whether the font outline is automatically adjusted.
         * @param { boolean } isForceAutoHinting - Indicates whether the font outline is automatically adjusted.
         * @throws { BusinessError } 401 - Parameter error. Possible causes: 1. Mandatory parameters are left unspecified;
         * <br>2. Incorrect parameter types.
         * @syscap SystemCapability.Graphics.Drawing
         * @since 12
         */
        setForceAutoHinting(isForceAutoHinting: boolean): void;
        /**
         * Gets whether the font outline is automatically adjusted.
         * @returns { boolean } Returns true if the font outline is automatically adjusted; returns false otherwise.
         * @syscap SystemCapability.Graphics.Drawing
         * @since 12
         */
        isForceAutoHinting(): boolean;
        /**
         * Retrieves the advance for each glyph in glyphs.
         * @param { Array<number> } glyphs - Array of glyph indices to be measured.
         * @returns { Array<number> } Returns the width of each character in a string.
         * @throws { BusinessError } 401 - Parameter error. Possible causes: 1. Mandatory parameters are left unspecified;
         * <br>2. Incorrect parameter types.
         * @syscap SystemCapability.Graphics.Drawing
         * @since 12
         */
        getWidths(glyphs: Array<number>): Array<number>;
        /**
         * Gets storage for glyph indexes.
         * @param { string } text - Indicates the character storage encoded with text encoding.
         * @param { number } glyphCount - The number of glyph. The default value is the result of calling countText.
         * @returns { Array<number> } Returns the storage for glyph indices.
         * @throws { BusinessError } 401 - Parameter error. Possible causes: 1. Mandatory parameters are left unspecified;
         * <br>2. Incorrect parameter types.
         * @syscap SystemCapability.Graphics.Drawing
         * @since 12
         */
        textToGlyphs(text: string, glyphCount?: number): Array<number>;
        /**
         * Returns true if glyphs may be drawn at sub-pixel offsets.
         * @returns { boolean } True if glyphs may be drawn at sub-pixel offsets.
         * @syscap SystemCapability.Graphics.Drawing
         * @since 12
         */
        isSubpixel(): boolean;
        /**
         * Returns true if font and glyph metrics are requested to be linearly scalable.
         * @returns { boolean } True if font and glyph metrics are requested to be linearly scalable.
         * @syscap SystemCapability.Graphics.Drawing
         * @since 12
         */
        isLinearMetrics(): boolean;
        /**
         * Returns text skew on x-axis.
         * @returns { number } Additional shear on x-axis relative to y-axis.
         * @syscap SystemCapability.Graphics.Drawing
         * @since 12
         */
        getSkewX(): number;
        /**
         * Gets whether to increase the stroke width to approximate bold fonts.
         * @returns { boolean } Returns true to increase the stroke width to approximate bold fonts;
         * returns false otherwise.
         * @syscap SystemCapability.Graphics.Drawing
         * @since 12
         */
        isEmbolden(): boolean;
        /**
         * Returns text scale on x-axis.
         * @returns { number } Text horizontal scale.
         * @syscap SystemCapability.Graphics.Drawing
         * @since 12
         */
        getScaleX(): number;
        /**
         * Gets font hinting pattern.
         * @returns { FontHinting } Font hinting level.
         * @syscap SystemCapability.Graphics.Drawing
         * @since 12
         */
        getHinting(): FontHinting;
        /**
         * Gets font edge pixels pattern.
         * @returns { FontEdging } Edge pixels pattern.
         * @syscap SystemCapability.Graphics.Drawing
         * @since 12
         */
        getEdging(): FontEdging;
    }
    /**
     * Indicate when certain metrics are valid; the underline or strikeout metrics may be valid and zero.
     * Fonts with embedded bitmaps may not have valid underline or strikeout metrics.
     * @enum { number }
     * @syscap SystemCapability.Graphics.Drawing
     * @since 12
     */
    enum FontMetricsFlags {
        /**
         * Set if underlineThickness of FontMetrics is valid.
         * @syscap SystemCapability.Graphics.Drawing
         * @since 12
         */
        UNDERLINE_THICKNESS_VALID = 1 << 0,
        /**
         * Set if underlinePosition of FontMetrics is valid.
         * @syscap SystemCapability.Graphics.Drawing
         * @since 12
         */
        UNDERLINE_POSITION_VALID = 1 << 1,
        /**
         * Set if strikethroughThickness of FontMetrics is valid.
         * @syscap SystemCapability.Graphics.Drawing
         * @since 12
         */
        STRIKETHROUGH_THICKNESS_VALID = 1 << 2,
        /**
         * Set if strikethroughPosition of FontMetrics is valid.
         * @syscap SystemCapability.Graphics.Drawing
         * @since 12
         */
        STRIKETHROUGH_POSITION_VALID = 1 << 3,
        /**
         * set if top, bottom, xMin, xMax of FontMetrics invalid.
         * @syscap SystemCapability.Graphics.Drawing
         * @since 12
         */
        BOUNDS_INVALID = 1 << 4
    }
    /**
     * The metrics of an Font.
     * @typedef FontMetrics
     * @syscap SystemCapability.Graphics.Drawing
     * @since 11
     */
    interface FontMetrics {
        /**
         * Indicating which metrics are valid.
         * @type { ?FontMetricsFlags }
         * @syscap SystemCapability.Graphics.Drawing
         * @since 12
         */
        flags?: FontMetricsFlags;
        /**
         * Maximum range above the glyph bounding box.
         * @type { number }
         * @syscap SystemCapability.Graphics.Drawing
         * @since 11
         */
        top: number;
        /**
         * Distance Retained Above Baseline.
         * @type { number }
         * @syscap SystemCapability.Graphics.Drawing
         * @since 11
         */
        ascent: number;
        /**
         * The distance that remains below the baseline.
         * @type { number }
         * @syscap SystemCapability.Graphics.Drawing
         * @since 11
         */
        descent: number;
        /**
         * Maximum range below the glyph bounding box.
         * @type { number }
         * @syscap SystemCapability.Graphics.Drawing
         * @since 11
         */
        bottom: number;
        /**
         * Line Spacing.
         * @type { number }
         * @syscap SystemCapability.Graphics.Drawing
         * @since 11
         */
        leading: number;
        /**
         * Average character width, zero if unknown.
         * @type { ?number }
         * @syscap SystemCapability.Graphics.Drawing
         * @since 12
         */
        avgCharWidth?: number;
        /**
         * Maximum character width, zero if unknown.
         * @type { ?number }
         * @syscap SystemCapability.Graphics.Drawing
         * @since 12
         */
        maxCharWidth?: number;
        /**
         * Greatest extent to left of origin of any glyph bounding box, typically negative; deprecated with variable fonts.
         * @type { ?number }
         * @syscap SystemCapability.Graphics.Drawing
         * @since 12
         */
        xMin?: number;
        /**
         * Greatest extent to right of origin of any glyph bounding box, typically positive; deprecated with variable fonts.
         * @type { ?number }
         * @syscap SystemCapability.Graphics.Drawing
         * @since 12
         */
        xMax?: number;
        /**
         * Height of lower-case 'x', zero if unknown, typically negative.
         * @type { ?number }
         * @syscap SystemCapability.Graphics.Drawing
         * @since 12
         */
        xHeight?: number;
        /**
         * Height of an upper-case letter, zero if unknown, typically negative.
         * @type { ?number }
         * @syscap SystemCapability.Graphics.Drawing
         * @since 12
         */
        capHeight?: number;
        /**
         * Underline thickness.
         * @type { ?number }
         * @syscap SystemCapability.Graphics.Drawing
         * @since 12
         */
        underlineThickness?: number;
        /**
         * Distance from baseline to top of stroke, typically positive.
         * @type { ?number }
         * @syscap SystemCapability.Graphics.Drawing
         * @since 12
         */
        underlinePosition?: number;
        /**
         * Strikethrough thickness.
         * @type { ?number }
         * @syscap SystemCapability.Graphics.Drawing
         * @since 12
         */
        strikethroughThickness?: number;
        /**
         * Distance from baseline to bottom of stroke, typically negative.
         * @type { ?number }
         * @syscap SystemCapability.Graphics.Drawing
         * @since 12
         */
        strikethroughPosition?: number;
    }
    /**
     * Lattice is the class for dividing an image into grids.
     * @syscap SystemCapability.Graphics.Drawing
     * @since 12
     */
    class Lattice {
        /**
         * Divide an image into a rectangular grid. Grid entries on even columns and even rows are fixed;
         * these entries are always drawn at their original size if the destination is large enough. If the destination
         * side is too small to hold the fixed entries, all fixed entries are scaled down to fit.
         * The grid entries not on even columns and rows are scaled to fit the remaining space, if any.
         * @param { Array<number> } xDivs - X coordinate of values used to divide the image.
         * @param { Array<number> } yDivs - Y coordinate of values used to divide the image.
         * @param { number } fXCount - Number of x coordinates. Must be >= 0.
         * @param { number } fYCount - Number of y coordinates. Must be >= 0.
         * @param { common2D.Rect | null } fBounds - Source bounds to draw from. The default value is null.
         * @param { Array<RectType> | null } fRectTypes - Array of fill types. The default value is null.
         * @param { Array<common2D.Color> | null } fColors - Array of colors. The default value is null.
         * @returns { Lattice } Lattice object.
         * @throws { BusinessError } 401 - Parameter error. Possible causes: 1. Mandatory parameters are left unspecified;
         * <br>2. Incorrect parameter types; 3. Parameter verification failed.
         * @static
         * @syscap SystemCapability.Graphics.Drawing
         * @since 12
         */
        static createImageLattice(xDivs: Array<number>, yDivs: Array<number>, fXCount: number, fYCount: number, fBounds?: common2D.Rect | null, fRectTypes?: Array<RectType> | null, fColors?: Array<common2D.Color> | null): Lattice;
    }
    /**
     * Enumerate rect types. Optional setting per rectangular grid entry to make it transparent,
     * or to fill the grid entry with a color. only used in Lattice.
     * @enum { number }
     * @syscap SystemCapability.Graphics.Drawing
     * @since 12
     */
    enum RectType {
        /**
         * Draws image into lattice rect.
         * @syscap SystemCapability.Graphics.Drawing
         * @since 12
         */
        DEFAULT = 0,
        /**
         * Skips lattice rect by making it transparent.
         * @syscap SystemCapability.Graphics.Drawing
         * @since 12
         */
        TRANSPARENT = 1,
        /**
         * Draws one of fColors into lattice rect.
         * @syscap SystemCapability.Graphics.Drawing
         * @since 12
         */
        FIXEDCOLOR = 2
    }
    /**
     * MaskFilter is the class for object that perform transformations on an alpha-channel mask before drawing it.
     * @syscap SystemCapability.Graphics.Drawing
     * @since 12
     */
    class MaskFilter {
        /**
         * Makes a MaskFilter with a blur effect.
         * @param { BlurType } blurType - Indicates the blur type.
         * @param { number } sigma - Indicates the standard deviation of the Gaussian blur to apply. Must be > 0.
         * @returns { MaskFilter } MaskFilter object.
         * @throws { BusinessError } 401 - Parameter error. Possible causes: 1. Mandatory parameters are left unspecified;
         * <br>2. Incorrect parameter types; 3. Parameter verification failed.
         * @static
         * @syscap SystemCapability.Graphics.Drawing
         * @since 12
         */
        static createBlurMaskFilter(blurType: BlurType, sigma: number): MaskFilter;
    }
    /**
     * Defines a PathEffect, which is used to affects stroked paths.
     * @syscap SystemCapability.Graphics.Drawing
     * @since 12
     */
    class PathEffect {
        /**
         * Makes a dash PathEffect.
         * @param { Array<number> } intervals - Array of ON and OFF distances. Must contain an even number of entries (>=2),
         * with the even indices specifying the "on" intervals, and the odd indices specifying the "off" intervals.
         * @param { number } phase - Offset into the intervals array.
         * @returns { PathEffect } PathEffect object.
         * @throws { BusinessError } 401 - Parameter error. Possible causes: 1. Mandatory parameters are left unspecified;
         * <br>2. Incorrect parameter types; 3. Parameter verification failed.
         * @static
         * @syscap SystemCapability.Graphics.Drawing
         * @since 12
         */
        static createDashPathEffect(intervals: Array<number>, phase: number): PathEffect;
        /**
         * Makes a corner PathEffect.
         * @param { number } radius - Indicates the radius of the tangent circle at the corners of the path.
         * The radius must be greater than 0.
         * @returns { PathEffect } PathEffect object.
         * @throws { BusinessError } 401 - Parameter error. Possible causes: 1. Mandatory parameters are left unspecified;
         * <br>2. Incorrect parameter types; 3. Parameter verification failed.
         * @static
         * @syscap SystemCapability.Graphics.Drawing
         * @since 12
         */
        static createCornerPathEffect(radius: number): PathEffect;
    }
    /**
     * Describes a shader effect object.
     * @syscap SystemCapability.Graphics.Drawing
     * @since 12
     */
    class ShaderEffect {
        /**
         * Creates an ShaderEffect object that generates a shader with single color.
         * @param { number } color - Indicates the color used by the shader.
         * @returns { ShaderEffect } Returns the shader with single color ShaderEffect object.
         * @throws { BusinessError } 401 - Parameter error. Possible causes: 1. Mandatory parameters are left unspecified;
         * <br>2. Incorrect parameter types.
         * @syscap SystemCapability.Graphics.Drawing
         * @since 12
         */
        static createColorShader(color: number): ShaderEffect;
        /**
         * Creates an ShaderEffect object that generates a linear gradient between the two specified points.
         * @param { common2D.Point } startPt - Indicates the start point for the gradient.
         * @param { common2D.Point } endPt - Indicates the end point for the gradient.
         * @param { Array<number> } colors - Indicates the colors to be distributed between the two points.
         * @param { TileMode } mode - Indicates the tile mode.
         * @param { Array<number> | null } pos - Indicates the relative position of each corresponding color
         * <br> in the colors array. The default value is empty for uniform distribution.
         * @param { Matrix | null } matrix - Indicates the Matrix object. The default value is null.
         * @returns { ShaderEffect } Returns a linear gradient ShaderEffect object.
         * @throws { BusinessError } 401 - Parameter error. Possible causes: 1. Mandatory parameters are left unspecified;
         * <br>2. Incorrect parameter types; 3. Parameter verification failed.
         * @syscap SystemCapability.Graphics.Drawing
         * @since 12
         */
        static createLinearGradient(startPt: common2D.Point, endPt: common2D.Point, colors: Array<number>, mode: TileMode, pos?: Array<number> | null, matrix?: Matrix | null): ShaderEffect;
        /**
         * Creates an ShaderEffect object that generates a radial gradient given the center and radius.
         * @param { common2D.Point } centerPt - Indicates the center of the circle for the gradient.
         * @param { number } radius - Indicates the radius of the circle for this gradient.
         * @param { Array<number> } colors - Indicates the colors to be distributed between the two points.
         * @param { TileMode } mode - Indicates the tile mode.
         * @param { Array<number> | null } pos - Indicates the relative position of each corresponding color
         * <br> in the colors array. The default value is empty for uniform distribution.
         * @param { Matrix | null } matrix - Indicates the Matrix object. The default value is null.
         * @returns { ShaderEffect } Returns a radial gradient ShaderEffect object.
         * @throws { BusinessError } 401 - Parameter error. Possible causes: 1. Mandatory parameters are left unspecified;
         * <br>2. Incorrect parameter types; 3. Parameter verification failed.
         * @syscap SystemCapability.Graphics.Drawing
         * @since 12
         */
        static createRadialGradient(centerPt: common2D.Point, radius: number, colors: Array<number>, mode: TileMode, pos?: Array<number> | null, matrix?: Matrix | null): ShaderEffect;
        /**
         * Creates an ShaderEffect object that generates a sweep gradient given a center.
         * @param { common2D.Point } centerPt - Indicates the center of the circle for the gradient.
         * @param { Array<number> } colors - Indicates the colors to be distributed between the two points.
         * @param { TileMode } mode - Indicates the tile mode.
         * @param { number } startAngle - The starting angle of the gradient.
         * @param { number } endAngle - The ending angle of the gradient.
         * @param { Array<number> | null } pos - Indicates the relative position of each corresponding color
         * <br> in the colors array. The default value is empty for uniform distribution.
         * @param { Matrix | null } matrix - Indicates the Matrix object. The default value is null.
         * @returns { ShaderEffect } Returns a sweep gradient ShaderEffect object.
         * @throws { BusinessError } 401 - Parameter error. Possible causes: 1. Mandatory parameters are left unspecified;
         * <br>2. Incorrect parameter types; 3. Parameter verification failed.
         * @syscap SystemCapability.Graphics.Drawing
         * @since 12
         */
        static createSweepGradient(centerPt: common2D.Point, colors: Array<number>, mode: TileMode, startAngle: number, endAngle: number, pos?: Array<number> | null, matrix?: Matrix | null): ShaderEffect;
        /**
         * Creates an ShaderEffect object that generates a conical gradient given two circles.
         * @param { common2D.Point } startPt - Indicates the center of the start circle for the gradient.
         * @param { number } startRadius - Indicates the radius of the start circle for this gradient.
         * @param { common2D.Point } endPt - Indicates the center of the end circle for the gradient.
         * @param { number } endRadius - Indicates the radius of the end circle for this gradient.
         * @param { Array<number> } colors - Indicates the colors to be distributed between the two points.
         * @param { TileMode } mode - Indicates the tile mode.
         * @param { Array<number> | null } pos - Indicates the relative position of each corresponding color
         * <br> in the colors array. The default value is empty for uniform distribution.
         * @param { Matrix | null } matrix - Indicates the Matrix object. The default value is null.
         * @returns { ShaderEffect } Returns a conical gradient ShaderEffect object.
         * @throws { BusinessError } 401 - Parameter error. Possible causes: 1. Mandatory parameters are left unspecified;
         * <br>2. Incorrect parameter types; 3. Parameter verification failed.
         * @syscap SystemCapability.Graphics.Drawing
         * @since 12
         */
        static createConicalGradient(startPt: common2D.Point, startRadius: number, endPt: common2D.Point, endRadius: number, colors: Array<number>, mode: TileMode, pos?: Array<number> | null, matrix?: Matrix | null): ShaderEffect;
    }
    /**
     * Enumerates tile modes that describe an image or texture.
     * @enum { number }
     * @syscap SystemCapability.Graphics.Drawing
     * @since 12
     */
    enum TileMode {
        /**
         * Replicate the edge color if the shader effect draws outside of its original bounds.
         * @syscap SystemCapability.Graphics.Drawing
         * @since 12
         */
        CLAMP = 0,
        /**
         * Repeat the shader effect image horizontally and vertically.
         * @syscap SystemCapability.Graphics.Drawing
         * @since 12
         */
        REPEAT = 1,
        /**
         * Repeat the shader effect image horizontally and vertically, alternating mirror images
         * so that adjacent images always seam.
         * @syscap SystemCapability.Graphics.Drawing
         * @since 12
         */
        MIRROR = 2,
        /**
         * Only draw within the original domain, return transparent-black everywhere else.
         * @syscap SystemCapability.Graphics.Drawing
         * @since 12
         */
        DECAL = 3
    }
    /**
     * Defines a ShadowLayer, which is used to specify the color, blur radius, and offset of the shadow.
     * @syscap SystemCapability.Graphics.Drawing
     * @since 12
     */
    class ShadowLayer {
        /**
         * Makes a new ShadowLayer.
         *
         * @param { number } blurRadius - The blur radius of the shadow. The blur radius must be greater than 0.
         * @param { number } x - The offset point on x-axis.
         * @param { number } y - The offset point on y-axis.
         * @param { common2D.Color } color - The shadow color. The range of color channels must be [0, 255].
         * @returns { ShadowLayer } ShadowLayer object.
         * @throws { BusinessError } 401 - Parameter error. Possible causes: 1. Mandatory parameters are left unspecified;
         * <br>2. Incorrect parameter types; 3. Parameter verification failed.
         * @static
         * @syscap SystemCapability.Graphics.Drawing
         * @since 12
         */
        static create(blurRadius: number, x: number, y: number, color: common2D.Color): ShadowLayer;
    }
    /**
     * ColorFilters are optional objects in the drawing pipeline.
     *
     * @syscap SystemCapability.Graphics.Drawing
     * @since 11
     */
    class ColorFilter {
        /**
         * Makes a color filter with the given color and blend mode.
         * @param { common2D.Color } color - The range of color channels must be [0, 255].
         * @param { BlendMode } mode - BlendMode.
         * @returns { ColorFilter } Colorfilter object.
         * @throws { BusinessError } 401 - Parameter error. Possible causes: 1. Mandatory parameters are left unspecified;
         * <br>2. Incorrect parameter types; 3. Parameter verification failed.
         * @static
         * @syscap SystemCapability.Graphics.Drawing
         * @since 11
         */
        static createBlendModeColorFilter(color: common2D.Color, mode: BlendMode): ColorFilter;
        /**
         * Create a color filter consisting of two filters.
         * @param { ColorFilter } outer - The filter is used next.
         * @param { ColorFilter } inner - The filter is used first.
         * @returns { ColorFilter } Colorfilter object.
         * @throws { BusinessError } 401 - Parameter error. Possible causes: 1. Mandatory parameters are left unspecified;
         * <br>2. Incorrect parameter types.
         * @static
         * @syscap SystemCapability.Graphics.Drawing
         * @since 11
         */
        static createComposeColorFilter(outer: ColorFilter, inner: ColorFilter): ColorFilter;
        /**
         * Makes a color filter that converts between linear colors and sRGB colors.
         * @returns { ColorFilter } Colorfilter object.
         * @static
         * @syscap SystemCapability.Graphics.Drawing
         * @since 11
         */
        static createLinearToSRGBGamma(): ColorFilter;
        /**
         * Makes a color filter that converts between sRGB colors and linear colors.
         * @returns { ColorFilter } Colorfilter object.
         * @static
         * @syscap SystemCapability.Graphics.Drawing
         * @since 11
         */
        static createSRGBGammaToLinear(): ColorFilter;
        /**
         * Makes a color filter that multiplies the luma of its input into the alpha channel,
         * and sets the red, green, and blue channels to zero.
         * @returns { ColorFilter } Colorfilter.
         * @static
         * @syscap SystemCapability.Graphics.Drawing
         * @since 11
         */
        static createLumaColorFilter(): ColorFilter;
        /**
         * Makes a color filter with a 5x4 color matrix
         * @param { Array<number> } matrix - Indicates the matrix, which is represented as a number array of length 20.
         * @returns { ColorFilter } Colorfilter object.
         * @throws { BusinessError } 401 - Parameter error. Possible causes: 1. Mandatory parameters are left unspecified;
         * <br>2. Incorrect parameter types; 3. Parameter verification failed.
         * @static
         * @syscap SystemCapability.Graphics.Drawing
         * @since 12
         */
        static createMatrixColorFilter(matrix: Array<number>): ColorFilter;
    }
    /**
     * ImageFilters are optional objects in the drawing pipeline.
     *
     * @syscap SystemCapability.Graphics.Drawing
     * @since 12
     */
    class ImageFilter {
        /**
         * Makes an ImageFilter object that blurs its input by the separate X and Y sigmas.
         * @param { number } sigmaX - Indicates the Gaussian sigma value for blurring along the X axis. Must be > 0.
         * @param { number } sigmaY - Indicates the Gaussian sigma value for blurring along the Y axis. Must be > 0.
         * @param { TileMode } tileMode - Indicates the tile mode applied at edges.
         * @param { ImageFilter | null } imageFilter - Indicates the input filter that is blurred,
         * uses source bitmap if this is null.
         * @returns { ImageFilter } ImageFilter object.
         * @throws { BusinessError } 401 - Parameter error. Possible causes: 1. Mandatory parameters are left unspecified;
         * <br>2. Incorrect parameter types; 3. Parameter verification failed.
         * @static
         * @syscap SystemCapability.Graphics.Drawing
         * @since 12
         */
        static createBlurImageFilter(sigmaX: number, sigmaY: number, tileMode: TileMode, imageFilter?: ImageFilter | null): ImageFilter;
        /**
         * Makes an ImageFilter object that applies the color filter to the input.
         * @param { ColorFilter } colorFilter - Indicates the color filter that transforms the input image.
         * @param { ImageFilter | null } imageFilter - Indicates the input filter, or uses the source bitmap if this is null.
         * @returns { ImageFilter } ImageFilter object.
         * @throws { BusinessError } 401 - Parameter error. Possible causes: 1. Mandatory parameters are left unspecified;
         * <br>2. Incorrect parameter types.
         * @static
         * @syscap SystemCapability.Graphics.Drawing
         * @since 12
         */
        static createFromColorFilter(colorFilter: ColorFilter, imageFilter?: ImageFilter | null): ImageFilter;
    }
    /**
     * Enumerate join styles. The join style defines the shape of the joins of a
     * polyline segment drawn by the pen.
     * @enum { number }
     * @syscap SystemCapability.Graphics.Drawing
     * @since 12
     */
    enum JoinStyle {
        /**
         * Miter corner. If the angle of a polyline is small, its miter length may be inappropriate.
         * In this case, you need to use the miter limit to limit the miter length.
         * @syscap SystemCapability.Graphics.Drawing
         * @since 12
         */
        MITER_JOIN = 0,
        /**
         * Round corner.
         * @syscap SystemCapability.Graphics.Drawing
         * @since 12
         */
        ROUND_JOIN = 1,
        /**
         * Bevel corner.
         * @syscap SystemCapability.Graphics.Drawing
         * @since 12
         */
        BEVEL_JOIN = 2
    }
    /**
     * Enumerates cap styles of a pen. The cap style defines
     * the style of both ends of a segment drawn by the pen.
     * @enum { number }
     * @syscap SystemCapability.Graphics.Drawing
     * @since 12
     */
    enum CapStyle {
        /**
         * No cap style. Both ends of the segment are cut off square.
         * @syscap SystemCapability.Graphics.Drawing
         * @since 12
         */
        FLAT_CAP = 0,
        /**
         * Square cap style. Both ends have a square, the height of which
         * is half of the width of the segment, with the same width.
         * @syscap SystemCapability.Graphics.Drawing
         * @since 12
         */
        SQUARE_CAP = 1,
        /**
         * Round cap style. Both ends have a semicircle centered, the diameter of which
         * is the same as the width of the segment.
         * @syscap SystemCapability.Graphics.Drawing
         * @since 12
         */
        ROUND_CAP = 2
    }
    /**
     * Enumerates blur type.
     * @enum { number }
     * @syscap SystemCapability.Graphics.Drawing
     * @since 12
     */
    enum BlurType {
        /**
         * Fuzzy inside and outside.
         * @syscap SystemCapability.Graphics.Drawing
         * @since 12
         */
        NORMAL = 0,
        /**
         * Solid inside, fuzzy outside.
         * @syscap SystemCapability.Graphics.Drawing
         * @since 12
         */
        SOLID = 1,
        /**
         * Nothing inside, fuzzy outside.
         * @syscap SystemCapability.Graphics.Drawing
         * @since 12
         */
        OUTER = 2,
        /**
         * Fuzzy inside, nothing outside.
         * @syscap SystemCapability.Graphics.Drawing
         * @since 12
         */
        INNER = 3
    }
    /**
     * Provides settings for strokes during drawing.
     * @syscap SystemCapability.Graphics.Drawing
     * @since 11
     */
    class Pen {
        /**
         * Constructor for the pen.
         * @syscap SystemCapability.Graphics.Drawing
         * @since 12
         */
        constructor();
        /**
         * Constructor for the pen from an existing pen object pen.
         * @param { Pen } pen - Indicates the Pen object.
         * @throws { BusinessError } 401 - Parameter error. Possible causes: 1. Mandatory parameters are left unspecified;
         * <br>2. Incorrect parameter types.
         * @syscap SystemCapability.Graphics.Drawing
         * @since 12
         */
        constructor(pen: Pen);
        /**
         * Sets the stroke miter limit for a polyline drawn by a pen.
         * @param { number } miter - Indicates a variable that describes the miter limit.
         * @throws { BusinessError } 401 - Parameter error. Possible causes: 1. Mandatory parameters are left unspecified;
         * <br>2. Incorrect parameter types.
         * @syscap SystemCapability.Graphics.Drawing
         * @since 12
         */
        setMiterLimit(miter: number): void;
        /**
         * Obtains the stroke miter limit of a polyline drawn by a pen.
         * @returns { number } Returns the miter limit.
         * @syscap SystemCapability.Graphics.Drawing
         * @since 12
         */
        getMiterLimit(): number;
        /**
         * Sets the shaderEffect for a pen.
         * @param { ShaderEffect } shaderEffect - Indicates the ShaderEffect object.
         * @throws { BusinessError } 401 - Parameter error. Possible causes: 1. Mandatory parameters are left unspecified;
         * <br>2. Incorrect parameter types.
         * @syscap SystemCapability.Graphics.Drawing
         * @since 12
         */
        setShaderEffect(shaderEffect: ShaderEffect): void;
        /**
        * Set the color of the pen.
        * @param { common2D.Color } color - The range of color channels must be [0, 255].
        * @throws { BusinessError } 401 - Parameter error. Possible causes: 1. Mandatory parameters are left unspecified;
        * <br>2. Incorrect parameter types; 3. Parameter verification failed.
        * @syscap SystemCapability.Graphics.Drawing
        * @since 11
        */
        setColor(color: common2D.Color): void;
        /**
        * Set the AGRB color of the pen.
         * @param { number } alpha - Alpha channel of color. The range of alpha must be [0, 255].
         * @param { number } red - Red channel of color. The range of red must be [0, 255].
         * @param { number } green - Green channel of color. The range of green must be [0, 255].
         * @param { number } blue - Blue channel of color. The range of blue must be [0, 255].
        * @throws { BusinessError } 401 - Parameter error. Possible causes: 1. Mandatory parameters are left unspecified;
         * <br>2. Incorrect parameter types.
        * @syscap SystemCapability.Graphics.Drawing
        * @since 12
        */
        setColor(alpha: number, red: number, green: number, blue: number): void;
        /**
         * Obtains the color of a pen. The color is used by the pen to outline a shape.
         * @returns { common2D.Color } Returns a 32-bit (ARGB) variable that describes the color.
         * @syscap SystemCapability.Graphics.Drawing
         * @since 12
         */
        getColor(): common2D.Color;
        /**
        * Sets the thickness of the pen used by the paint to outline the shape.
        *
        * @param { number } width - Zero thickness for hairline; greater than zero for pen thickness.
        * @throws { BusinessError } 401 - Parameter error. Possible causes: 1. Mandatory parameters are left unspecified;
        * <br>2. Incorrect parameter types.
        * @syscap SystemCapability.Graphics.Drawing
        * @since 11
        */
        setStrokeWidth(width: number): void;
        /**
         * Obtains the thickness of a pen. This thickness determines the width of the outline of a shape.
         * @returns { number } Returns the thickness.
         * @syscap SystemCapability.Graphics.Drawing
         * @since 12
         */
        getWidth(): number;
        /**
        * Requests, but does not require, that edge pixels draw opaque or with partial transparency.
        *
        * @param { boolean } aa - Setting for antialiasing.
        * @throws { BusinessError } 401 - Parameter error. Possible causes: 1. Mandatory parameters are left unspecified;
        * <br>2. Incorrect parameter types.
        * @syscap SystemCapability.Graphics.Drawing
        * @since 11
        */
        setAntiAlias(aa: boolean): void;
        /**
         * Checks whether anti-aliasing is enabled for a pen. If anti-aliasing is enabled,
         * edges will be drawn with partial transparency.
         * @returns { boolean } Returns true if the anti-aliasing is enabled; returns false otherwise.
         * @syscap SystemCapability.Graphics.Drawing
         * @since 12
         */
        isAntiAlias(): boolean;
        /**
        * Replaces alpha, leaving RGB
        *
        * @param { number } alpha - Alpha channel of color. The range of alpha must be [0, 255].
        * @throws { BusinessError } 401 - Parameter error. Possible causes: 1. Mandatory parameters are left unspecified;
        * <br>2. Incorrect parameter types; 3. Parameter verification failed.
        * @syscap SystemCapability.Graphics.Drawing
        * @since 11
        */
        setAlpha(alpha: number): void;
        /**
         * Obtains the alpha of a pen. The alpha is used by the pen to outline a shape.
         * @returns { number } Returns a 8-bit variable that describes the alpha.
         * @syscap SystemCapability.Graphics.Drawing
         * @since 12
         */
        getAlpha(): number;
        /**
        * Sets ColorFilter to pen
        *
        * @param { ColorFilter } filter - ColorFilter to apply to subsequent draw.
        * @throws { BusinessError } 401 - Parameter error. Possible causes: 1. Mandatory parameters are left unspecified;
        * <br>2. Incorrect parameter types.
        * @syscap SystemCapability.Graphics.Drawing
        * @since 11
        */
        setColorFilter(filter: ColorFilter): void;
        /**
         * Gets ColorFilter of pen
         * @returns { ColorFilter } ColorFilter.
         * @syscap SystemCapability.Graphics.Drawing
         * @since 12
         */
        getColorFilter(): ColorFilter;
        /**
         * Sets ImageFilter to pen
         * @param { ImageFilter | null } filter - ImageFilter to apply to subsequent draw.
         * @throws { BusinessError } 401 - Parameter error. Possible causes: 1. Mandatory parameters are left unspecified;
         * <br>2. Incorrect parameter types.
         * @syscap SystemCapability.Graphics.Drawing
         * @since 12
         */
        setImageFilter(filter: ImageFilter | null): void;
        /**
         * Sets MaskFilter to pen.
         *
         * @param { MaskFilter } filter - MaskFilter to apply to subsequent draw.
         * @throws { BusinessError } 401 - Parameter error. Possible causes: 1. Mandatory parameters are left unspecified;
         * <br>2. Incorrect parameter types.
         * @syscap SystemCapability.Graphics.Drawing
         * @since 12
         */
        setMaskFilter(filter: MaskFilter): void;
        /**
         * Sets PathEffect to pen.
         *
         * @param { PathEffect } effect - PathEffect to apply to subsequent draw.
         * @throws { BusinessError } 401 - Parameter error. Possible causes: 1. Mandatory parameters are left unspecified;
         * <br>2. Incorrect parameter types.
         * @syscap SystemCapability.Graphics.Drawing
         * @since 12
         */
        setPathEffect(effect: PathEffect): void;
        /**
         * Sets ShadowLayer to pen.
         *
         * @param { ShadowLayer } shadowLayer - ShadowLayer to apply to subsequent draw.
         * @throws { BusinessError } 401 - Parameter error. Possible causes: 1. Mandatory parameters are left unspecified;
         * <br>2. Incorrect parameter types.
         * @syscap SystemCapability.Graphics.Drawing
         * @since 12
         */
        setShadowLayer(shadowLayer: ShadowLayer): void;
        /**
        * Sets a blender that implements the specified blendmode enum.
        *
        * @param { BlendMode } mode - Blendmode.
        * @throws { BusinessError } 401 - Parameter error. Possible causes: 1. Mandatory parameters are left unspecified;
        * <br>2. Incorrect parameter types; 3. Parameter verification failed.
        * @syscap SystemCapability.Graphics.Drawing
        * @since 11
        */
        setBlendMode(mode: BlendMode): void;
        /**
        * Request color distribution error.
        *
        * @param { boolean } dither - Whether the color is distributed incorrectly.
        * @throws { BusinessError } 401 - Parameter error. Possible causes: 1. Mandatory parameters are left unspecified;
        * <br>2. Incorrect parameter types.
        * @syscap SystemCapability.Graphics.Drawing
        * @since 11
        */
        setDither(dither: boolean): void;
        /**
         * Sets the JoinStyle for a pen.
         *
         * @param { JoinStyle } style - The JoinStyle.
         * @throws { BusinessError } 401 - Parameter error. Possible causes: 1. Mandatory parameters are left unspecified;
         * <br>2. Incorrect parameter types; 3. Parameter verification failed.
         * @syscap SystemCapability.Graphics.Drawing
         * @since 12
         */
        setJoinStyle(style: JoinStyle): void;
        /**
         * Obtains the JoinStyle of a pen.
         *
         * @returns { JoinStyle } The JoinStyle.
         * @syscap SystemCapability.Graphics.Drawing
         * @since 12
         */
        getJoinStyle(): JoinStyle;
        /**
         * Sets the CapStyle for a pen.
         *
         * @param { CapStyle } style - The CapStyle.
         * @throws { BusinessError } 401 - Parameter error. Possible causes: 1. Mandatory parameters are left unspecified;
         * <br>2. Incorrect parameter types; 3. Parameter verification failed.
         * @syscap SystemCapability.Graphics.Drawing
         * @since 12
         */
        setCapStyle(style: CapStyle): void;
        /**
         * Obtains the CapStyle of a pen.
         *
         * @returns { CapStyle } The CapStyle.
         * @syscap SystemCapability.Graphics.Drawing
         * @since 12
         */
        getCapStyle(): CapStyle;
        /**
         * Resets all pen contents to their initial values.
         * @syscap SystemCapability.Graphics.Drawing
         * @since 12
         */
        reset(): void;
        /**
         * Obtains the filled equivalent of the src path.
         *
         * @param { Path } src - The path read to create a filled version.
         * @param { Path } dst - The resulting path (may be the same as src).
         * @returns { boolean } true if the path should be filled, or false if it should be drawn with a hairline (width == 0)
         * @throws { BusinessError } 401 - Parameter error. Possible causes: 1. Mandatory parameters are left unspecified;
         * <br>2. Incorrect parameter types.
         * @syscap SystemCapability.Graphics.Drawing
         * @since 12
         */
        getFillPath(src: Path, dst: Path): boolean;
    }
    /**
     * Provides settings for brush fill when drawing.
     * @syscap SystemCapability.Graphics.Drawing
     * @since 11
     */
    class Brush {
        /**
         * Constructor for the Brush.
         * @syscap SystemCapability.Graphics.Drawing
         * @since 12
         */
        constructor();
        /**
         * Constructor for the Brush from an existing brush object brush.
         * @param { Brush } brush - Indicates the Brush object.
         * @throws { BusinessError } 401 - Parameter error. Possible causes: 1. Mandatory parameters are left unspecified;
         * <br>2. Incorrect parameter types.
         * @syscap SystemCapability.Graphics.Drawing
         * @since 12
         */
        constructor(brush: Brush);
        /**
         * Set the color of the brush.
         * @param { common2D.Color } color - The range of color channels must be [0, 255].
         * @throws { BusinessError } 401 - Parameter error. Possible causes: 1. Mandatory parameters are left unspecified;
         * <br>2. Incorrect parameter types; 3. Parameter verification failed.
         * @syscap SystemCapability.Graphics.Drawing
         * @since 11
         */
        setColor(color: common2D.Color): void;
        /**
         * Set the ARGB color of the brush.
         * @param { number } alpha - Alpha channel of color. The range of alpha must be [0, 255].
         * @param { number } red - Red channel of color. The range of red must be [0, 255].
         * @param { number } green - Green channel of color. The range of green must be [0, 255].
         * @param { number } blue - Blue channel of color. The range of blue must be [0, 255].
         * @throws { BusinessError } 401 - Parameter error. Possible causes: 1. Mandatory parameters are left unspecified;
         * <br>2. Incorrect parameter types; 3. Parameter verification failed.
         * @syscap SystemCapability.Graphics.Drawing
         * @since 12
         */
        setColor(alpha: number, red: number, green: number, blue: number): void;
        /**
         * Obtains the color of a brush. The color is used by the brush to fill in a shape.
         * @returns { common2D.Color } Returns a 32-bit (ARGB) variable that describes the color.
         * @syscap SystemCapability.Graphics.Drawing
         * @since 12
         */
        getColor(): common2D.Color;
        /**
         * Requests, but does not require, that edge pixels draw opaque or with partial transparency.
         * @param { boolean } aa - Setting for antialiasing.
         * @throws { BusinessError } 401 - Parameter error. Possible causes: 1. Mandatory parameters are left unspecified;
         * <br>2. Incorrect parameter types.
         * @syscap SystemCapability.Graphics.Drawing
         * @since 11
         */
        setAntiAlias(aa: boolean): void;
        /**
         * Checks whether anti-aliasing is enabled for a brush. If anti-aliasing is enabled,
         * edges will be drawn with partial transparency.
         * @returns { boolean } Returns true if anti-aliasing is enabled; returns false otherwise.
         * @syscap SystemCapability.Graphics.Drawing
         * @since 12
         */
        isAntiAlias(): boolean;
        /**
         * Replaces alpha, leaving RGB
         * @param { number } alpha - Alpha channel of color. The range of alpha must be [0, 255].
         * @throws { BusinessError } 401 - Parameter error. Possible causes: 1. Mandatory parameters are left unspecified;
         * <br>2. Incorrect parameter types; 3. Parameter verification failed.
         * @syscap SystemCapability.Graphics.Drawing
         * @since 11
         */
        setAlpha(alpha: number): void;
        /**
         * Obtains the alpha of a brush. The alpha is used by the brush to fill in a shape.
         * @returns { number } Returns a 8-bit variable that describes the alpha.
         * @syscap SystemCapability.Graphics.Drawing
         * @since 12
         */
        getAlpha(): number;
        /**
         * Sets ColorFilter to brush
         * @param { ColorFilter } filter - ColorFilter to apply to subsequent draw.
         * @throws { BusinessError } 401 - Parameter error. Possible causes: 1. Mandatory parameters are left unspecified;
         * <br>2. Incorrect parameter types.
         * @syscap SystemCapability.Graphics.Drawing
         * @since 11
         */
        setColorFilter(filter: ColorFilter): void;
        /**
         * Gets ColorFilter of brush
         * @returns { ColorFilter } ColorFilter.
         * @syscap SystemCapability.Graphics.Drawing
         * @since 12
         */
        getColorFilter(): ColorFilter;
        /**
         * Sets ImageFilter to brush
         * @param { ImageFilter | null } filter - ImageFilter to apply to subsequent draw.
         * @throws { BusinessError } 401 - Parameter error. Possible causes: 1. Mandatory parameters are left unspecified;
         * <br>2. Incorrect parameter types.
         * @syscap SystemCapability.Graphics.Drawing
         * @since 12
         */
        setImageFilter(filter: ImageFilter | null): void;
        /**
         * Sets MaskFilter to brush.
         * @param { MaskFilter } filter - MaskFilter to apply to subsequent draw.
         * @throws { BusinessError } 401 - Parameter error. Possible causes: 1. Mandatory parameters are left unspecified;
         * <br>2. Incorrect parameter types.
         * @syscap SystemCapability.Graphics.Drawing
         * @since 12
         */
        setMaskFilter(filter: MaskFilter): void;
        /**
         * Sets ShadowLayer to brush.
         *
         * @param { ShadowLayer } shadowLayer - ShadowLayer painting.
         * @throws { BusinessError } 401 - Parameter error. Possible causes: 1. Mandatory parameters are left unspecified;
         * <br>2. Incorrect parameter types.
         * @syscap SystemCapability.Graphics.Drawing
         * @since 12
         */
        setShadowLayer(shadowLayer: ShadowLayer): void;
        /**
         * Sets the shaderEffect for a brush.
         * @param { ShaderEffect } shaderEffect - Indicates the ShaderEffect object.
         * @throws { BusinessError } 401 - Parameter error. Possible causes: 1. Mandatory parameters are left unspecified;
         * <br>2. Incorrect parameter types.
         * @syscap SystemCapability.Graphics.Drawing
         * @since 12
         */
        setShaderEffect(shaderEffect: ShaderEffect): void;
        /**
         * Sets a blender that implements the specified blendmode enum.
         * @param { BlendMode } mode - Blendmode.
         * @throws { BusinessError } 401 - Parameter error. Possible causes: 1. Mandatory parameters are left unspecified;
         * <br>2. Incorrect parameter types; 3. Parameter verification failed.
         * @syscap SystemCapability.Graphics.Drawing
         * @since 11
         */
        setBlendMode(mode: BlendMode): void;
        /**
         * Resets all brush contents to their initial values.
         * @syscap SystemCapability.Graphics.Drawing
         * @since 12
         */
        reset(): void;
    }
    /**
     * Declares functions related to the matrix object in the drawing module.
     *
     * @syscap SystemCapability.Graphics.Drawing
     * @since 12
     */
    class Matrix {
        /**
         * Creates an identity matrix.
         * @syscap SystemCapability.Graphics.Drawing
         * @since 12
         */
        constructor();
        /**
         * Sets matrix to rotate by degrees about a pivot point at (px, py).
         * @param { number } degree - Indicates the angle of axes relative to upright axes.
         * @param { number } px - Indicates the pivot on x-axis.
         * @param { number } py - Indicates the pivot on y-axis.
         * @throws { BusinessError } 401 - Parameter error. Possible causes: 1. Mandatory parameters are left unspecified;
         * <br>2. Incorrect parameter types.
         * @syscap SystemCapability.Graphics.Drawing
         * @since 12
         */
        setRotation(degree: number, px: number, py: number): void;
        /**
         * Sets matrix to scale by sx and sy, about a pivot point at (px, py).
         * @param { number } sx - Indicates the horizontal scale factor.
         * @param { number } sy - Indicates the vertical scale factor.
         * @param { number } px - Indicates the pivot on x-axis.
         * @param { number } py - Indicates the pivot on y-axis.
         * @throws { BusinessError } 401 - Parameter error. Possible causes: 1. Mandatory parameters are left unspecified;
         * <br>2. Incorrect parameter types.
         * @syscap SystemCapability.Graphics.Drawing
         * @since 12
         */
        setScale(sx: number, sy: number, px: number, py: number): void;
        /**
         * Sets matrix to translate by (dx, dy).
         * @param { number } dx - Indicates the horizontal translation.
         * @param { number } dy - Indicates the vertical translation.
         * @throws { BusinessError } 401 - Parameter error. Possible causes: 1. Mandatory parameters are left unspecified;
         * <br>2. Incorrect parameter types.
         * @syscap SystemCapability.Graphics.Drawing
         * @since 12
         */
        setTranslation(dx: number, dy: number): void;
        /**
         * Sets the params for a matrix.
         * @param { Array<number> } values - Each value in the array represents the following parameters:
         * values[0] - horizontal scale factor to store.
         * values[1] - horizontal skew factor to store.
         * values[2] - horizontal translation to store.
         * values[3] - vertical skew factor to store.
         * values[4] - vertical scale factor to store.
         * values[5] - vertical translation to store.
         * values[6] - input x-axis values perspective factor to store.
         * values[7] - input y-axis values perspective factor to store.
         * values[8] - perspective scale factor to store.
         * @throws { BusinessError } 401 - Parameter error. Possible causes: 1. Mandatory parameters are left unspecified;
         * <br>2. Incorrect parameter types; 3. Parameter verification failed.
         * @syscap SystemCapability.Graphics.Drawing
         * @since 12
         */
        setMatrix(values: Array<number>): void;
        /**
         * Sets matrix total to matrix a multiplied by matrix b.
         * @param { Matrix } matrix - Indicates the Matrix object.
         * @throws { BusinessError } 401 - Parameter error. Possible causes: 1. Mandatory parameters are left unspecified;
         * <br>2. Incorrect parameter types.
         * @syscap SystemCapability.Graphics.Drawing
         * @since 12
         */
        preConcat(matrix: Matrix): void;
        /**
         * Returns true if the first matrix equals the second matrix.
         * @param { Matrix } matrix - Indicates the Matrix object.
         * @returns { Boolean } Returns true if the two matrices are equal; returns false otherwise.
         * @throws { BusinessError } 401 - Parameter error. Possible causes: 1. Mandatory parameters are left unspecified;
         * <br>2. Incorrect parameter types.
         * @syscap SystemCapability.Graphics.Drawing
         * @since 12
         */
        isEqual(matrix: Matrix): Boolean;
        /**
         * Sets inverse to reciprocal matrix, returning true if matrix can be inverted.
         * @param { Matrix } matrix - Indicates the Matrix object.
         * @returns { Boolean } Returns true if matrix can be inverted; returns false otherwise.
         * @throws { BusinessError } 401 - Parameter error. Possible causes: 1. Mandatory parameters are left unspecified;
         * <br>2. Incorrect parameter types.
         * @syscap SystemCapability.Graphics.Drawing
         * @since 12
         */
        invert(matrix: Matrix): Boolean;
        /**
         * Returns true if matrix is identity.
         * @returns { Boolean } Returns true if matrix is identity; returns false otherwise.
         * @syscap SystemCapability.Graphics.Drawing
         * @since 12
         */
        isIdentity(): Boolean;
        /**
         * Get one matrix value. Index is between the range of 0-8.
         * @param { number } index - one of 0-8
         * @returns { number } Returns value corresponding to index.Returns 0 if out of range.
         * @throws { BusinessError } 401 - Parameter error. Possible causes: 1. Mandatory parameters are left unspecified;
         * <br>2. Incorrect parameter types; 3. Parameter verification failed.
         * @syscap SystemCapability.Graphics.Drawing
         * @since 12
         */
        getValue(index: number): number;
        /**
         * Sets matrix to matrix multiplied by matrix constructed from rotating by degrees around pivot point (px, py).
         * This can be thought of as rotating around a pivot point after applying matrix.
         * @param { number } degree - Indicates the angle of axes relative to upright axes.
         * @param { number } px - Indicates the pivot on x-axis.
         * @param { number } py - Indicates the pivot on y-axis.
         * @throws { BusinessError } 401 - Parameter error. Possible causes: 1. Mandatory parameters are left unspecified;
         * <br>2. Incorrect parameter types.
         * @syscap SystemCapability.Graphics.Drawing
         * @since 12
         */
        postRotate(degree: number, px: number, py: number): void;
        /**
         * Sets matrix to matrix multiplied by matrix constructed from scaling by (sx, sy) relative to pivot point (px, py).
         * This can be thought of as scaling relative to a pivot point after applying matrix.
         * @param { number } sx - Indicates the horizontal scale factor.
         * @param { number } sy - Indicates the vertical scale factor.
         * @param { number } px - Indicates the pivot on x-axis.
         * @param { number } py - Indicates the pivot on y-axis.
         * @throws { BusinessError } 401 - Parameter error. Possible causes: 1. Mandatory parameters are left unspecified;
         * <br>2. Incorrect parameter types.
         * @syscap SystemCapability.Graphics.Drawing
         * @since 12
         */
        postScale(sx: number, sy: number, px: number, py: number): void;
        /**
         * Sets matrix to matrix multiplied by matrix constructed from translation (dx, dy).
         * This can be thought of as moving the point to be mapped after applying matrix.
         * @param { number } dx - Indicates the horizontal translation.
         * @param { number } dy - Indicates the vertical translation.
         * @throws { BusinessError } 401 - Parameter error. Possible causes: 1. Mandatory parameters are left unspecified;
         * <br>2. Incorrect parameter types.
         * @syscap SystemCapability.Graphics.Drawing
         * @since 12
         */
        postTranslate(dx: number, dy: number): void;
        /**
         * Sets matrix to matrix multiplied by matrix constructed from rotating by degrees around pivot point (px, py).
         * This can be thought of as rotating around a pivot point before applying matrix.
         * @param { number } degree - Indicates the angle of axes relative to upright axes.
         * @param { number } px - Indicates the pivot on x-axis.
         * @param { number } py - Indicates the pivot on y-axis.
         * @throws { BusinessError } 401 - Parameter error. Possible causes: 1. Mandatory parameters are left unspecified;
         * <br>2. Incorrect parameter types.
         * @syscap SystemCapability.Graphics.Drawing
         * @since 12
         */
        preRotate(degree: number, px: number, py: number): void;
        /**
         * Sets matrix to matrix multiplied by matrix constructed from scaling by (sx, sy) relative to pivot point (px, py).
         * This can be thought of as scaling relative to a pivot point before applying matrix.
         * @param { number } sx - Indicates the horizontal scale factor.
         * @param { number } sy - Indicates the vertical scale factor.
         * @param { number } px - Indicates the pivot on x-axis.
         * @param { number } py - Indicates the pivot on y-axis.
         * @throws { BusinessError } 401 - Parameter error. Possible causes: 1. Mandatory parameters are left unspecified;
         * <br>2. Incorrect parameter types.
         * @syscap SystemCapability.Graphics.Drawing
         * @since 12
         */
        preScale(sx: number, sy: number, px: number, py: number): void;
        /**
         * Sets matrix to matrix multiplied by matrix constructed from translation (dx, dy).
         * This can be thought of as moving the point to be mapped before applying matrix.
         * @param { number } dx - Indicates the horizontal translation.
         * @param { number } dy - Indicates the vertical translation.
         * @throws { BusinessError } 401 - Parameter error. Possible causes: 1. Mandatory parameters are left unspecified;
         * <br>2. Incorrect parameter types.
         * @syscap SystemCapability.Graphics.Drawing
         * @since 12
         */
        preTranslate(dx: number, dy: number): void;
        /**
         * Reset matrix to identity.
         * @syscap SystemCapability.Graphics.Drawing
         * @since 12
         */
        reset(): void;
        /**
         * Maps src array of length count to dst array of equal or greater length.
         * This can be thought of as moving the point to be mapped before applying matrix.
         * @param { Array<common2D.Point> } src - points to transform.
         * @returns { Array<common2D.Point> } Return mapped points array.
         * @throws { BusinessError } 401 - Parameter error. Possible causes: 1. Mandatory parameters are left unspecified;
         * <br>2. Incorrect parameter types.
         * @syscap SystemCapability.Graphics.Drawing
         * @since 12
         */
        mapPoints(src: Array<common2D.Point>): Array<common2D.Point>;
        /**
         * Return nine scalar values contained by Matrix.
         * @returns { Array<number> } nine scalar values contained by Matrix.
         * @syscap SystemCapability.Graphics.Drawing
         * @since 12
         */
        getAll(): Array<number>;
        /**
         * Sets dst to bounds of src corners mapped by matrix transformation.
         * @param { common2D.Rect } dst - Rect to map from.
         * @param { common2D.Rect } src - Rect to map to.
         * @returns { boolean } Returns true if the mapped src is equal to the dst; returns false is not equal.
         * @throws { BusinessError } 401 - Parameter error. Possible causes: 1. Mandatory parameters are left unspecified;
         * <br>2. Incorrect parameter types.
         * @syscap SystemCapability.Graphics.Drawing
         * @since 12
         */
        mapRect(dst: common2D.Rect, src: common2D.Rect): boolean;
        /**
         * Sets matrix to scale and translate src rect to dst rect.
         * @param { common2D.Rect } src - Rect to map from.
         * @param { common2D.Rect } dst - Rect to map to.
         * @param { ScaleToFit } scaleToFit - Describes how matrix is constructed to map one rect to another.
         * @returns { boolean } Returns true if dst is empty, and sets matrix to:
                   | 0 0 0 |
                   | 0 0 0 |
                   | 0 0 1 |.
         * @throws { BusinessError } 401 - Parameter error. Possible causes: 1. Mandatory parameters are left unspecified;
         * <br>2. Incorrect parameter types; 3. Parameter verification failed.
         * @syscap SystemCapability.Graphics.Drawing
         * @since 12
         */
        setRectToRect(src: common2D.Rect, dst: common2D.Rect, scaleToFit: ScaleToFit): boolean;
        /**
         * Sets Matrix to map src to dst. Count must be zero or greater, and four or less.
         * @param { Array<common2D.Point> } src - Point to map from
         * @param { Array<common2D.Point> } dst - Point to map to
         * @param { number } count - Number of Point in src and dst
         * @returns { boolean } Returns true if Matrix was constructed successfully
         * @throws { BusinessError } 401 - Parameter error. Possible causes: 1. Mandatory parameters are left unspecified;
         * <br>2. Incorrect parameter types.
         * @syscap SystemCapability.Graphics.Drawing
         * @since 12
         */
        setPolyToPoly(src: Array<common2D.Point>, dst: Array<common2D.Point>, count: number): boolean;
    }
    /**
     * Describes a scale-to-fit values.
     * @enum { number }
     * @syscap SystemCapability.Graphics.Drawing
     * @since 12
     */
    enum ScaleToFit {
        /**
         * Scales in x and y to fill destination Rect.
         * @syscap SystemCapability.Graphics.Drawing
         * @since 12
         */
        FILL_SCALE_TO_FIT = 0,
        /**
         * Scales and aligns to left and top.
         * @syscap SystemCapability.Graphics.Drawing
         * @since 12
         */
        START_SCALE_TO_FIT = 1,
        /**
         * Scales and aligns to center.
         * @syscap SystemCapability.Graphics.Drawing
         * @since 12
         */
        CENTER_SCALE_TO_FIT = 2,
        /**
         * Scales and aligns to right and bottom.
         * @syscap SystemCapability.Graphics.Drawing
         * @since 12
         */
        END_SCALE_TO_FIT = 3
    }
    /**
     * Describes a region object.
     * @syscap SystemCapability.Graphics.Drawing
     * @since 12
     */
    class Region {
        /**
         * Determines whether the test point is in the region.
         * @param { number } x - Indicates the x coordinate of the point. The parameter must be an integer.
         * @param { number } y - Indicates the y coordinate of the point. The parameter must be an integer.
         * @returns { boolean } Returns true if (x, y) is inside region; returns false otherwise.
         * @throws { BusinessError } 401 - Parameter error. Possible causes: 1. Mandatory parameters are left unspecified;
         * <br>2. Incorrect parameter types.
         * @syscap SystemCapability.Graphics.Drawing
         * @since 12
         */
        isPointContained(x: number, y: number): boolean;
        /**
         * Determines whether other region is in the region.
         * @param { Region } other - Other region object.
         * @returns { boolean } Returns true if other region is completely inside the region object;
         * <br>returns false otherwise.
         * @throws { BusinessError } 401 - Parameter error. Possible causes: 1. Mandatory parameters are left unspecified;
         * <br>2. Incorrect parameter types.
         * @syscap SystemCapability.Graphics.Drawing
         * @since 12
         */
        isRegionContained(other: Region): boolean;
        /**
         * Replaces region with the result of region op region.
         * @param { Region } region - Region object.
         * @param { RegionOp } regionOp - Operation type.
         * @returns { boolean } Returns true if replaced region is not empty; returns false otherwise.
         * @throws { BusinessError } 401 - Parameter error. Possible causes: 1. Mandatory parameters are left unspecified;
         * <br>2. Incorrect parameter types.
         * @syscap SystemCapability.Graphics.Drawing
         * @since 12
         */
        op(region: Region, regionOp: RegionOp): boolean;
        /**
         * Determines whether rect and region does not intersect.
         * @param { number } left - Left position of rectangle. The parameter must be an integer.
         * @param { number } top - Top position of rectangle. The parameter must be an integer.
         * @param { number } right - Right position of rectangle. The parameter must be an integer.
         * @param { number } bottom - Bottom position of rectangle. The parameter must be an integer.
         * @returns { boolean } Returns true if rect and region is not intersect; returns false otherwise.
         * @throws { BusinessError } 401 - Parameter error. Possible causes: 1. Mandatory parameters are left unspecified;
         * <br>2. Incorrect parameter types.
         * @syscap SystemCapability.Graphics.Drawing
         * @since 12
         */
        quickReject(left: number, top: number, right: number, bottom: number): boolean;
        /**
         * Sets the region to match outline of path within clip.
         * @param { Path } path - Providing outline.
         * @param { Region } clip - Region object.
         * @returns { boolean } Returns true if constructed region is not empty; returns false otherwise.
         * @throws { BusinessError } 401 - Parameter error. Possible causes: 1. Mandatory parameters are left unspecified;
         * <br>2. Incorrect parameter types.
         * @syscap SystemCapability.Graphics.Drawing
         * @since 12
         */
        setPath(path: Path, clip: Region): boolean;
        /**
         * Sets a rect to region.
         * @param { number } left - Left position of rectangle. The parameter must be an integer.
         * @param { number } top - Top position of rectangle. The parameter must be an integer.
         * @param { number } right - Right position of rectangle. The parameter must be an integer.
         * @param { number } bottom - Bottom position of rectangle. The parameter must be an integer.
         * @returns { boolean } Returns true if constructed region is not empty; returns false otherwise.
         * @throws { BusinessError } 401 - Parameter error. Possible causes: 1. Mandatory parameters are left unspecified;
         * <br>2. Incorrect parameter types.
         * @syscap SystemCapability.Graphics.Drawing
         * @since 12
         */
        setRect(left: number, top: number, right: number, bottom: number): boolean;
    }
    /**
     * Enumerates of operations when two regions are combined.
     * @enum { number }
     * @syscap SystemCapability.Graphics.Drawing
     * @since 12
     */
    enum RegionOp {
        /**
         * Difference operation.
         * @syscap SystemCapability.Graphics.Drawing
         * @since 12
         */
        DIFFERENCE = 0,
        /**
         * Intersect operation.
         * @syscap SystemCapability.Graphics.Drawing
         * @since 12
         */
        INTERSECT = 1,
        /**
         * Union operation.
         * @syscap SystemCapability.Graphics.Drawing
         * @since 12
         */
        UNION = 2,
        /**
         * Xor operation.
         * @syscap SystemCapability.Graphics.Drawing
         * @since 12
         */
        XOR = 3,
        /**
         * Reverse difference operation.
         * @syscap SystemCapability.Graphics.Drawing
         * @since 12
         */
        REVERSE_DIFFERENCE = 4,
        /**
         * Replace operation.
         * @syscap SystemCapability.Graphics.Drawing
         * @since 12
         */
        REPLACE = 5
    }
    /**
     * Enumerates of corner radius position.
     *
     * @enum { number }
     * @syscap SystemCapability.Graphics.Drawing
     * @since 12
     */
    enum CornerPos {
        /**
         * Index of top-left corner radius.
         * @syscap SystemCapability.Graphics.Drawing
         * @since 12
         */
        TOP_LEFT_POS = 0,
        /**
         * Index of top-right corner radius.
         * @syscap SystemCapability.Graphics.Drawing
         * @since 12
         */
        TOP_RIGHT_POS = 1,
        /**
         * Index of bottom-right corner radius.
         * @syscap SystemCapability.Graphics.Drawing
         * @since 12
         */
        BOTTOM_RIGHT_POS = 2,
        /**
         * Index of bottom-left corner radius.
         * @syscap SystemCapability.Graphics.Drawing
         * @since 12
         */
        BOTTOM_LEFT_POS = 3
    }
    /**
     * Enumeration defines the constraint type.
     *
     * @enum { number }
     * @syscap SystemCapability.Graphics.Drawing
     * @since 12
     */
    enum SrcRectConstraint {
        /**
         * Using sampling only inside bounds in a slower manner.
         *
         * @syscap SystemCapability.Graphics.Drawing
         * @since 12
         */
        STRICT = 0,
        /**
         * Using sampling outside bounds in a faster manner.
         *
         * @syscap SystemCapability.Graphics.Drawing
         * @since 12
         */
        FAST = 1
    }
}
export default drawing;
