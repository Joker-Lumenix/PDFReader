package java.awt

import java.awt.geom.AffineTransform
import java.awt.geom.PathIterator
import java.awt.geom.Point2D
import java.awt.geom.Rectangle2D

/**
 * The types of the abstract window toolkit of Java, as a stand in for Android.
 *
 * Apache POI describes the position of the shapes of a legacy document with
 * the types of Java. Android does not know them, so the app carries the few
 * types that the readers of POI need.
 *
 * The classes of this file are a part of the compatibility layer of the app,
 * the app itself does not use them. They follow the hierarchy of Java, so the
 * reader of POI finds the types it expects.
 */

/** The outline of a shape of Java. */
interface Shape {
    fun getBounds(): Rectangle

    fun contains(x: kotlin.Double, y: kotlin.Double): Boolean

    fun contains(point: Point2D): Boolean

    fun contains(rect: Rectangle2D): Boolean

    fun intersects(x: kotlin.Double, y: kotlin.Double, width: kotlin.Double, height: kotlin.Double): Boolean

    fun intersects(rect: Rectangle2D): Boolean

    fun getPathIterator(at: AffineTransform?): PathIterator

    fun createTransformedShape(tx: AffineTransform): Shape
}

/** The rectangle of Java, the position of the shapes of a document is described with it. */
class Rectangle @JvmOverloads constructor(
    @JvmField var x: Int = 0,
    @JvmField var y: Int = 0,
    @JvmField var width: Int = 0,
    @JvmField var height: Int = 0,
) : Rectangle2D() {

    constructor(x: kotlin.Double, y: kotlin.Double, width: kotlin.Double, height: kotlin.Double) : this(
        x.toInt(),
        y.toInt(),
        width.toInt(),
        height.toInt(),
    )

    constructor(rect: Rectangle?) : this(
        rect?.x ?: 0,
        rect?.y ?: 0,
        rect?.width ?: 0,
        rect?.height ?: 0,
    )

    override fun getX(): kotlin.Double = x.toDouble()

    override fun getY(): kotlin.Double = y.toDouble()

    override fun getWidth(): kotlin.Double = width.toDouble()

    override fun getHeight(): kotlin.Double = height.toDouble()

    override fun setRect(x: kotlin.Double, y: kotlin.Double, width: kotlin.Double, height: kotlin.Double) {
        this.x = x.toInt()
        this.y = y.toInt()
        this.width = width.toInt()
        this.height = height.toInt()
    }

    override fun setBounds(x: kotlin.Double, y: kotlin.Double, width: kotlin.Double, height: kotlin.Double) {
        setRect(x, y, width, height)
    }

    override fun setBounds(rect: Rectangle2D) = setRect(
        rect.getX(),
        rect.getY(),
        rect.getWidth(),
        rect.getHeight(),
    )

    val minX: Int get() = x
    val minY: Int get() = y
    val maxX: Int get() = x + width
    val maxY: Int get() = y + height
    val centerX: Int get() = x + width / 2
    val centerY: Int get() = y + height / 2

    fun setX(value: Int) {
        x = value
    }

    fun setY(value: Int) {
        y = value
    }

    fun setWidth(value: Int) {
        width = value
    }

    fun setHeight(value: Int) {
        height = value
    }

    fun setBounds(x: Int, y: Int, width: Int, height: Int) {
        this.x = x
        this.y = y
        this.width = width
        this.height = height
    }

    fun setBounds(rect: Rectangle) = setBounds(rect.x, rect.y, rect.width, rect.height)

    fun setLocation(x: Int, y: Int) {
        this.x = x
        this.y = y
    }

    fun setLocation(point: Point) = setLocation(point.x, point.y)

    override fun getBounds(): Rectangle = Rectangle(x, y, width, height)

    fun getLocation(): Point = Point(x, y)

    fun getSize(): Dimension = Dimension(width, height)

    fun setSize(width: Int, height: Int) {
        this.width = width
        this.height = height
    }

    fun setSize(size: Dimension) = setSize(size.width, size.height)

    fun translate(dx: Int, dy: Int) = setBounds(x + dx, y + dy, width, height)

    fun contains(x: Int, y: Int): Boolean =
        x >= this.x && x < this.x + width && y >= this.y && y < this.y + height

    fun contains(point: Point): Boolean = contains(point.x, point.y)

    fun contains(rect: Rectangle): Boolean =
        rect.x >= x && rect.y >= y &&
            rect.x + rect.width <= x + width &&
            rect.y + rect.height <= y + height

    fun intersects(rect: Rectangle): Boolean =
        rect.x < x + width && x < rect.x + rect.width &&
            rect.y < y + height && y < rect.y + rect.height

    fun isEmpty(): Boolean = width <= 0 || height <= 0

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        val rect = other as? Rectangle ?: return false
        return x == rect.x && y == rect.y && width == rect.width && height == rect.height
    }

    override fun hashCode(): Int {
        var result = x
        result = 31 * result + y
        result = 31 * result + width
        result = 31 * result + height
        return result
    }

    override fun toString(): String = "Rectangle[x=$x,y=$y,width=$width,height=$height]"
}

/** The point of Java. */
class Point @JvmOverloads constructor(
    @JvmField var x: Int = 0,
    @JvmField var y: Int = 0,
) : Point2D() {

    constructor(x: kotlin.Double, y: kotlin.Double) : this(x.toInt(), y.toInt())

    constructor(point: Point?) : this(point?.x ?: 0, point?.y ?: 0)

    override fun getX(): kotlin.Double = x.toDouble()

    override fun getY(): kotlin.Double = y.toDouble()

    override fun setLocation(x: kotlin.Double, y: kotlin.Double) {
        this.x = x.toInt()
        this.y = y.toInt()
    }

    fun setX(value: Int) {
        x = value
    }

    fun setY(value: Int) {
        y = value
    }

    fun setLocation(x: Int, y: Int) {
        this.x = x
        this.y = y
    }

    fun setLocation(point: Point) = setLocation(point.x, point.y)

    fun translate(dx: Int, dy: Int) {
        x += dx
        y += dy
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        val point = other as? Point ?: return false
        return x == point.x && y == point.y
    }

    override fun hashCode(): Int = x * 31 + y

    override fun toString(): String = "Point[x=$x,y=$y]"
}

/** The size of a shape of Java. */
class Dimension @JvmOverloads constructor(
    @JvmField var width: Int = 0,
    @JvmField var height: Int = 0,
) {
    constructor(width: kotlin.Double, height: kotlin.Double) : this(width.toInt(), height.toInt())

    constructor(size: Dimension?) : this(size?.width ?: 0, size?.height ?: 0)

    fun getWidth(): Int = width

    fun getHeight(): Int = height

    fun setSize(width: Int, height: Int) {
        this.width = width
        this.height = height
    }

    fun setSize(size: Dimension) = setSize(size.width, size.height)

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        val size = other as? Dimension ?: return false
        return width == size.width && height == size.height
    }

    override fun hashCode(): Int = width * 31 + height

    override fun toString(): String = "Dimension[width=$width,height=$height]"
}

/** The empty borders of a shape of Java. */
class Insets @JvmOverloads constructor(
    @JvmField var top: Int = 0,
    @JvmField var left: Int = 0,
    @JvmField var bottom: Int = 0,
    @JvmField var right: Int = 0,
) {
    fun getTop(): Int = top

    fun getLeft(): Int = left

    fun getBottom(): Int = bottom

    fun getRight(): Int = right

    fun setInsets(top: Int, left: Int, bottom: Int, right: Int) {
        this.top = top
        this.left = left
        this.bottom = bottom
        this.right = right
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        val insets = other as? Insets ?: return false
        return top == insets.top && left == insets.left &&
            bottom == insets.bottom && right == insets.right
    }

    override fun hashCode(): Int {
        var result = top
        result = 31 * result + left
        result = 31 * result + bottom
        result = 31 * result + right
        return result
    }
}

/** The colour of Java, the reader of POI hands the colours of a document out this way. */
class Color(
    @JvmField var red: Int,
    @JvmField var green: Int,
    @JvmField var blue: Int,
    @JvmField var alpha: Int,
) {

    constructor(red: Int, green: Int, blue: Int) : this(red, green, blue, 255)

    constructor(rgb: Int) : this(
        (rgb shr 16) and 0xFF,
        (rgb shr 8) and 0xFF,
        rgb and 0xFF,
        255,
    )

    constructor(rgb: Int, alpha: Int) : this(
        (rgb shr 16) and 0xFF,
        (rgb shr 8) and 0xFF,
        rgb and 0xFF,
        alpha,
    )

    constructor(color: Color?) : this(
        color?.red ?: 0,
        color?.green ?: 0,
        color?.blue ?: 0,
        color?.alpha ?: 255,
    )

    fun getRed(): Int = red

    fun getGreen(): Int = green

    fun getBlue(): Int = blue

    fun getAlpha(): Int = alpha

    fun getRGB(): Int = (alpha shl 24) or (red shl 16) or (green shl 8) or blue

    fun brighter(): Color = Color(
        (red * (255 - red) / 255).coerceIn(0, 255),
        (green * (255 - green) / 255).coerceIn(0, 255),
        (blue * (255 - blue) / 255).coerceIn(0, 255),
        alpha,
    )

    fun darker(): Color = Color(red * 4 / 5, green * 4 / 5, blue * 4 / 5, alpha)

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        val color = other as? Color ?: return false
        return red == color.red && green == color.green && blue == color.blue && alpha == color.alpha
    }

    override fun hashCode(): Int {
        var result = red
        result = 31 * result + green
        result = 31 * result + blue
        result = 31 * result + alpha
        return result
    }

    override fun toString(): String = "Color[r=$red,g=$green,b=$blue,a=$alpha]"

    companion object {
        @JvmField val WHITE = Color(255, 255, 255)
        @JvmField val BLACK = Color(0, 0, 0)
        @JvmField val RED = Color(255, 0, 0)
        @JvmField val GREEN = Color(0, 255, 0)
        @JvmField val BLUE = Color(0, 0, 255)
        @JvmField val LIGHT_GRAY = Color(192, 192, 192)
        @JvmField val GRAY = Color(128, 128, 128)
        @JvmField val DARK_GRAY = Color(64, 64, 64)
        @JvmField val YELLOW = Color(255, 255, 0)
        @JvmField val CYAN = Color(0, 255, 255)
        @JvmField val MAGENTA = Color(255, 0, 255)
        @JvmField val ORANGE = Color(255, 200, 0)
        @JvmField val PINK = Color(255, 175, 175)
    }
}

/** The paint of Java, the reader of POI hands the colours of a shape out this way. */
interface Paint

/** The line of a shape of Java. */
interface Stroke

/** The image of Java. */
abstract class Image {
    abstract fun getWidth(): Int

    abstract fun getHeight(): Int

    abstract fun getGraphics(): Graphics?
}

/** The drawing surface of Java. */
abstract class Graphics {
    abstract fun getColor(): Color?

    abstract fun setColor(color: Color?)

    abstract fun translate(x: Int, y: Int)

    abstract fun fillRect(x: Int, y: Int, width: Int, height: Int)

    abstract fun drawImage(image: Image, x: Int, y: Int, width: Int, height: Int, observer: Any?): Boolean
}

/** The drawing surface of Java, the one that knows the shapes. */
abstract class Graphics2D : Graphics(), Paint, Stroke {
    abstract fun draw(shape: Shape)

    abstract fun fill(shape: Shape)

    abstract fun drawLine(x1: Int, y1: Int, x2: Int, y2: Int)

    abstract fun setStroke(stroke: Stroke?)

    abstract fun getStroke(): Stroke?
}
