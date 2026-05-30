package studio.freestyle.labs.danjiangsunseeker.domain.model

/**
 * 預設 / 使用者新增的拍攝熱點。
 *
 * @param nameRes 字串資源 ID — null 代表使用者自訂熱點，使用 [customName]
 */
data class Hotspot(
    val id: String,
    val nameRes: Int?,
    val customName: String?,
    val position: GeoPoint,
    val description: String = "",
    val accessNote: String = "",
    /** 預設熱點的本地化描述資源；自訂熱點為 null，改用 [description]。 */
    val descriptionRes: Int? = null,
    /** 預設熱點的本地化交通/通行備註資源；無則為 null。 */
    val accessNoteRes: Int? = null,
) {
    val isCustom: Boolean get() = nameRes == null
}
