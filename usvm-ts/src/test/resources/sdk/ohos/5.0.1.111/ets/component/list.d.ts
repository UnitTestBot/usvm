/*
 * Copyright (c) 2021-2023 Huawei Device Co., Ltd.
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
 * @kit ArkUI
 */
/**
 * Declare scroll status
 *
 * @enum { number }
 * @syscap SystemCapability.ArkUI.ArkUI.Full
 * @since 7
 */
/**
 * Declare scroll status
 *
 * @enum { number }
 * @syscap SystemCapability.ArkUI.ArkUI.Full
 * @form
 * @since 9
 */
/**
 * Declare scroll status
 *
 * @enum { number }
 * @syscap SystemCapability.ArkUI.ArkUI.Full
 * @crossplatform
 * @form
 * @since 10
 */
/**
 * Declare scroll status
 *
 * @enum { number }
 * @syscap SystemCapability.ArkUI.ArkUI.Full
 * @crossplatform
 * @form
 * @atomicservice
 * @since 11
 */
declare enum ScrollState {
    /**
     * Not activated.
     *
     * @syscap SystemCapability.ArkUI.ArkUI.Full
     * @since 7
     */
    /**
     * Not activated.
     *
     * @syscap SystemCapability.ArkUI.ArkUI.Full
     * @form
     * @since 9
     */
    /**
     * Not activated.
     *
     * @syscap SystemCapability.ArkUI.ArkUI.Full
     * @crossplatform
     * @form
     * @since 10
     */
    /**
     * Not activated.
     *
     * @syscap SystemCapability.ArkUI.ArkUI.Full
     * @crossplatform
     * @form
     * @atomicservice
     * @since 11
     */
    Idle,
    /**
     * Scrolling status.
     *
     * @syscap SystemCapability.ArkUI.ArkUI.Full
     * @since 7
     */
    /**
     * Scrolling status.
     *
     * @syscap SystemCapability.ArkUI.ArkUI.Full
     * @form
     * @since 9
     */
    /**
     * Scrolling status.
     *
     * @syscap SystemCapability.ArkUI.ArkUI.Full
     * @crossplatform
     * @form
     * @since 10
     */
    /**
     * Scrolling status.
     *
     * @syscap SystemCapability.ArkUI.ArkUI.Full
     * @crossplatform
     * @form
     * @atomicservice
     * @since 11
     */
    Scroll,
    /**
     * Drag status.
     *
     * @syscap SystemCapability.ArkUI.ArkUI.Full
     * @since 7
     */
    /**
     * Drag status.
     *
     * @syscap SystemCapability.ArkUI.ArkUI.Full
     * @form
     * @since 9
     */
    /**
     * Drag status.
     *
     * @syscap SystemCapability.ArkUI.ArkUI.Full
     * @crossplatform
     * @form
     * @since 10
     */
    /**
     * Drag status.
     *
     * @syscap SystemCapability.ArkUI.ArkUI.Full
     * @crossplatform
     * @form
     * @atomicservice
     * @since 11
     */
    Fling
}
/**
 * Declare list item alignment status
 *
 * @enum { number }
 * @syscap SystemCapability.ArkUI.ArkUI.Full
 * @form
 * @since 9
 */
/**
 * Declare list item alignment status
 *
 * @enum { number }
 * @syscap SystemCapability.ArkUI.ArkUI.Full
 * @crossplatform
 * @form
 * @since 10
 */
/**
 * Declare list item alignment status
 *
 * @enum { number }
 * @syscap SystemCapability.ArkUI.ArkUI.Full
 * @crossplatform
 * @form
 * @atomicservice
 * @since 11
 */
declare enum ListItemAlign {
    /**
     * Start position in the direction of cross axis.
     *
     * @syscap SystemCapability.ArkUI.ArkUI.Full
     * @form
     * @since 9
     */
    /**
     * Start position in the direction of cross axis.
     *
     * @syscap SystemCapability.ArkUI.ArkUI.Full
     * @crossplatform
     * @form
     * @since 10
     */
    /**
     * Start position in the direction of cross axis.
     *
     * @syscap SystemCapability.ArkUI.ArkUI.Full
     * @crossplatform
     * @form
     * @atomicservice
     * @since 11
     */
    Start,
    /**
     * Center position in the direction of cross axis.
     *
     * @syscap SystemCapability.ArkUI.ArkUI.Full
     * @form
     * @since 9
     */
    /**
     * Center position in the direction of cross axis.
     *
     * @syscap SystemCapability.ArkUI.ArkUI.Full
     * @crossplatform
     * @form
     * @since 10
     */
    /**
     * Center position in the direction of cross axis.
     *
     * @syscap SystemCapability.ArkUI.ArkUI.Full
     * @crossplatform
     * @form
     * @atomicservice
     * @since 11
     */
    Center,
    /**
     * End position in the direction of cross axis
     *
     * @syscap SystemCapability.ArkUI.ArkUI.Full
     * @form
     * @since 9
     */
    /**
     * End position in the direction of cross axis
     *
     * @syscap SystemCapability.ArkUI.ArkUI.Full
     * @crossplatform
     * @form
     * @since 10
     */
    /**
     * End position in the direction of cross axis
     *
     * @syscap SystemCapability.ArkUI.ArkUI.Full
     * @crossplatform
     * @form
     * @atomicservice
     * @since 11
     */
    End
}
/**
 * Declare list item group area
 *
 * @enum { number }
 * @syscap SystemCapability.ArkUI.ArkUI.Full
 * @crossplatform
 * @atomicservice
 * @since 12
 */
declare enum ListItemGroupArea {
    /**
     * List item group area is none
     *
     * @syscap SystemCapability.ArkUI.ArkUI.Full
     * @crossplatform
     * @atomicservice
     * @since 12
     */
    NONE = 0,
    /**
     * List item group area is list item
     *
     * @syscap SystemCapability.ArkUI.ArkUI.Full
     * @crossplatform
     * @atomicservice
     * @since 12
     */
    IN_LIST_ITEM_AREA = 1,
    /**
     * List item group area is header
     *
     * @syscap SystemCapability.ArkUI.ArkUI.Full
     * @crossplatform
     * @atomicservice
     * @since 12
     */
    IN_HEADER_AREA = 2,
    /**
     * List item group area is footer
     *
     * @syscap SystemCapability.ArkUI.ArkUI.Full
     * @crossplatform
     * @atomicservice
     * @since 12
     */
    IN_FOOTER_AREA = 3
}
/**
 * Declare item group sticky style.
 *
 * @enum { number }
 * @syscap SystemCapability.ArkUI.ArkUI.Full
 * @form
 * @since 9
 */
/**
 * Declare item group sticky style.
 *
 * @enum { number }
 * @syscap SystemCapability.ArkUI.ArkUI.Full
 * @crossplatform
 * @form
 * @since 10
 */
/**
 * Declare item group sticky style.
 *
 * @enum { number }
 * @syscap SystemCapability.ArkUI.ArkUI.Full
 * @crossplatform
 * @form
 * @atomicservice
 * @since 11
 */
declare enum StickyStyle {
    /**
     * The header and footer of each item group will not be pinned.
     *
     * @syscap SystemCapability.ArkUI.ArkUI.Full
     * @form
     * @since 9
     */
    /**
     * The header and footer of each item group will not be pinned.
     *
     * @syscap SystemCapability.ArkUI.ArkUI.Full
     * @crossplatform
     * @form
     * @since 10
     */
    /**
     * The header and footer of each item group will not be pinned.
     *
     * @syscap SystemCapability.ArkUI.ArkUI.Full
     * @crossplatform
     * @form
     * @atomicservice
     * @since 11
     */
    None = 0,
    /**
     * The header of each item group will be pinned.
     *
     * @syscap SystemCapability.ArkUI.ArkUI.Full
     * @form
     * @since 9
     */
    /**
     * The header of each item group will be pinned.
     *
     * @syscap SystemCapability.ArkUI.ArkUI.Full
     * @crossplatform
     * @form
     * @since 10
     */
    /**
     * The header of each item group will be pinned.
     *
     * @syscap SystemCapability.ArkUI.ArkUI.Full
     * @crossplatform
     * @form
     * @atomicservice
     * @since 11
     */
    Header = 1,
    /**
     * The footer of each item group will be pinned.
     *
     * @syscap SystemCapability.ArkUI.ArkUI.Full
     * @form
     * @since 9
     */
    /**
     * The footer of each item group will be pinned.
     *
     * @syscap SystemCapability.ArkUI.ArkUI.Full
     * @crossplatform
     * @form
     * @since 10
     */
    /**
     * The footer of each item group will be pinned.
     *
     * @syscap SystemCapability.ArkUI.ArkUI.Full
     * @crossplatform
     * @form
     * @atomicservice
     * @since 11
     */
    Footer = 2
}
/**
 * Declare limited position when scroll end.
 *
 * @enum { number }
 * @syscap SystemCapability.ArkUI.ArkUI.Full
 * @since 10
 */
/**
 * Declare limited position when scroll end.
 *
 * @enum { number }
 * @syscap SystemCapability.ArkUI.ArkUI.Full
 * @crossplatform
 * @atomicservice
 * @since 11
 */
declare enum ScrollSnapAlign {
    /**
     * Default no item scroll snap alignment effect. When scroll end,
     * list item will stop without limit.
     *
     * @syscap SystemCapability.ArkUI.ArkUI.Full
     * @since 10
     */
    /**
     * Default no item scroll snap alignment effect. When scroll end,
     * list item will stop without limit.
     *
     * @syscap SystemCapability.ArkUI.ArkUI.Full
     * @crossplatform
     * @atomicservice
     * @since 11
     */
    NONE,
    /**
     * The first item in view will be aligned at the start of list.
     *
     * @syscap SystemCapability.ArkUI.ArkUI.Full
     * @since 10
     */
    /**
     * The first item in view will be aligned at the start of list.
     *
     * @syscap SystemCapability.ArkUI.ArkUI.Full
     * @crossplatform
     * @atomicservice
     * @since 11
     */
    START,
    /**
     * The middle item in view will be aligned at the center of list.
     *
     * @syscap SystemCapability.ArkUI.ArkUI.Full
     * @since 10
     */
    /**
     * The middle item in view will be aligned at the center of list.
     *
     * @syscap SystemCapability.ArkUI.ArkUI.Full
     * @crossplatform
     * @atomicservice
     * @since 11
     */
    CENTER,
    /**
     * The last item in view will be aligned at the end of list.
     *
     * @syscap SystemCapability.ArkUI.ArkUI.Full
     * @since 10
     */
    /**
     * The last item in view will be aligned at the end of list.
     *
     * @syscap SystemCapability.ArkUI.ArkUI.Full
     * @crossplatform
     * @atomicservice
     * @since 11
     */
    END
}
/**
 * Defines the close swipe action options.
 *
 * @interface CloseSwipeActionOptions
 * @syscap SystemCapability.ArkUI.ArkUI.Full
 * @crossplatform
 * @since 11
 */
/**
 * Defines the close swipe action options.
 *
 * @interface CloseSwipeActionOptions
 * @syscap SystemCapability.ArkUI.ArkUI.Full
 * @crossplatform
 * @atomicservice
 * @since 12
 */
declare interface CloseSwipeActionOptions {
    /**
     * Called after collapse animation completed.
     *
     * @type { ?function }
     * @syscap SystemCapability.ArkUI.ArkUI.Full
     * @crossplatform
     * @since 11
     */
    /**
     * Called after collapse animation completed.
     *
     * @type { ?function }
     * @syscap SystemCapability.ArkUI.ArkUI.Full
     * @crossplatform
     * @atomicservice
     * @since 12
     */
    onFinish?: () => void;
}
/**
 * Defines the visible list content info.
 *
 * @interface VisibleListContentInfo
 * @syscap SystemCapability.ArkUI.ArkUI.Full
 * @crossplatform
 * @atomicservice
 * @since 12
 */
declare interface VisibleListContentInfo {
    /**
     * Index number of a child in the list.
     *
     * @type { number }
     * @syscap SystemCapability.ArkUI.ArkUI.Full
     * @crossplatform
     * @atomicservice
     * @since 12
     */
    index: number;
    /**
     * Area of the ListItemGroup.
     *
     * @type { ?ListItemGroupArea }
     * @syscap SystemCapability.ArkUI.ArkUI.Full
     * @crossplatform
     * @atomicservice
     * @since 12
     */
    itemGroupArea?: ListItemGroupArea;
    /**
     * Index number of a ListItem in ListItemGroup.
     *
     * @type { ?number }
     * @syscap SystemCapability.ArkUI.ArkUI.Full
     * @crossplatform
     * @atomicservice
     * @since 12
     */
    itemIndexInGroup?: number;
}
/**
 * Callback of scroll visible content, using in onScrollVisibleContentChange.
 *
 * @typedef {function} OnScrollVisibleContentChangeCallback
 * @syscap SystemCapability.ArkUI.ArkUI.Full
 * @crossplatform
 * @atomicservice
 * @since 12
 */
declare type OnScrollVisibleContentChangeCallback = (start: VisibleListContentInfo, end: VisibleListContentInfo) => void;
/**
 * @extends Scroller
 * @syscap SystemCapability.ArkUI.ArkUI.Full
 * @crossplatform
 * @since 11
 */
/**
 * @extends Scroller
 * @syscap SystemCapability.ArkUI.ArkUI.Full
 * @crossplatform
 * @atomicservice
 * @since 12
 */
declare class ListScroller extends Scroller {
    /**
     * Gets the size and position of a ListItem in a ListItemGroup.
     *
     * @param { number } index - Index of the ListItemGroup in List.
     * @param { number } indexInGroup - Index of the ListItem in ListItemGroup.
     * @returns { RectResult } Returns the size and position.
     * @throws { BusinessError } 401 - Parameter error. Possible causes:
     * <br> 1. Mandatory parameters are left unspecified.
     * <br> 2. Incorrect parameters types.
     * <br> 3. Parameter verification failed.
     * @throws { BusinessError } 100004 - Controller not bound to component.
     * @syscap SystemCapability.ArkUI.ArkUI.Full
     * @crossplatform
     * @since 11
     */
    /**
     * Gets the size and position of a ListItem in a ListItemGroup.
     *
     * @param { number } index - Index of the ListItemGroup in List.
     * @param { number } indexInGroup - Index of the ListItem in ListItemGroup.
     * @returns { RectResult } Returns the size and position.
     * @throws { BusinessError } 401 - Parameter error. Possible causes:
     * <br> 1. Mandatory parameters are left unspecified.
     * <br> 2. Incorrect parameters types.
     * <br> 3. Parameter verification failed.
     * @throws { BusinessError } 100004 - Controller not bound to component.
     * @syscap SystemCapability.ArkUI.ArkUI.Full
     * @crossplatform
     * @atomicservice
     * @since 12
     */
    getItemRectInGroup(index: number, indexInGroup: number): RectResult;
    /**
     * Called when sliding to the specified index in specified ListItemGroup.
     *
     * @param { number } index - Index of the ListItemGroup in List.
     * @param { number } indexInGroup - Index of the ListItem in ListItemGroup.
     * @param { boolean } smooth - If true, scroll to index item with animation. If false, scroll to index item without animation.
     * @param { ScrollAlign } align - Sets the alignment mode of a specified index.
     * @throws { BusinessError } 401 - Parameter error. Possible causes:
     * <br> 1. Mandatory parameters are left unspecified.
     * <br> 2. Incorrect parameters types.
     * <br> 3. Parameter verification failed.
     * @throws { BusinessError } 100004 - Controller not bound to component.
     * @syscap SystemCapability.ArkUI.ArkUI.Full
     * @crossplatform
     * @since 11
     */
    /**
     * Called when sliding to the specified index in specified ListItemGroup.
     *
     * @param { number } index - Index of the ListItemGroup in List.
     * @param { number } indexInGroup - Index of the ListItem in ListItemGroup.
     * @param { boolean } smooth - If true, scroll to index item with animation. If false, scroll to index item without animation.
     * @param { ScrollAlign } align - Sets the alignment mode of a specified index.
     * @throws { BusinessError } 401 - Parameter error. Possible causes:
     * <br> 1. Mandatory parameters are left unspecified.
     * <br> 2. Incorrect parameters types.
     * <br> 3. Parameter verification failed.
     * @throws { BusinessError } 100004 - Controller not bound to component.
     * @syscap SystemCapability.ArkUI.ArkUI.Full
     * @crossplatform
     * @atomicservice
     * @since 12
     */
    scrollToItemInGroup(index: number, indexInGroup: number, smooth?: boolean, align?: ScrollAlign): void;
    /**
     * Collapse all listItem.
     *
     * @param { CloseSwipeActionOptions } options - Options of close Swipe items.
     * @throws { BusinessError } 401 - Parameter error. Possible causes:
     * <br> 1. Mandatory parameters are left unspecified.
     * <br> 2. Incorrect parameters types.
     * <br> 3. Parameter verification failed.
     * @throws { BusinessError } 100004 - Controller not bound to component.
     * @syscap SystemCapability.ArkUI.ArkUI.Full
     * @crossplatform
     * @since 11
     */
    /**
     * Collapse all listItem.
     *
     * @param { CloseSwipeActionOptions } options - Options of close Swipe items.
     * @throws { BusinessError } 401 - Parameter error. Possible causes:
     * <br> 1. Mandatory parameters are left unspecified.
     * <br> 2. Incorrect parameters types.
     * <br> 3. Parameter verification failed.
     * @throws { BusinessError } 100004 - Controller not bound to component.
     * @syscap SystemCapability.ArkUI.ArkUI.Full
     * @crossplatform
     * @atomicservice
     * @since 12
     */
    closeAllSwipeActions(options?: CloseSwipeActionOptions): void;
}
/**
 * The list interface is extended.
 *
 * @interface ListInterface
 * @syscap SystemCapability.ArkUI.ArkUI.Full
 * @since 7
 */
/**
 * The list interface is extended.
 *
 * @interface ListInterface
 * @syscap SystemCapability.ArkUI.ArkUI.Full
 * @form
 * @since 9
 */
/**
 * The list interface is extended.
 *
 * @interface ListInterface
 * @syscap SystemCapability.ArkUI.ArkUI.Full
 * @crossplatform
 * @form
 * @since 10
 */
/**
 * The list interface is extended.
 *
 * @interface ListInterface
 * @syscap SystemCapability.ArkUI.ArkUI.Full
 * @crossplatform
 * @form
 * @atomicservice
 * @since 11
 */
interface ListInterface {
    /**
     * Called when interface data is called.
     *
     * @param { object } value
     * @returns { ListAttribute }
     * @syscap SystemCapability.ArkUI.ArkUI.Full
     * @since 7
     */
    /**
     * Called when interface data is called.
     *
     * @param { object } value
     * @returns { ListAttribute }
     * @syscap SystemCapability.ArkUI.ArkUI.Full
     * @form
     * @since 9
     */
    /**
     * Called when interface data is called.
     *
     * @param { object } value
     * @returns { ListAttribute }
     * @syscap SystemCapability.ArkUI.ArkUI.Full
     * @crossplatform
     * @form
     * @since 10
     */
    /**
     * Called when interface data is called.
     *
     * @param { object } value
     * @returns { ListAttribute }
     * @syscap SystemCapability.ArkUI.ArkUI.Full
     * @crossplatform
     * @form
     * @atomicservice
     * @since 11
     */
    (value?: {
        initialIndex?: number;
        space?: number | string;
        scroller?: Scroller;
    }): ListAttribute;
}
/**
 * @extends CommonMethod<ListAttribute>
 * @syscap SystemCapability.ArkUI.ArkUI.Full
 * @since 7
 */
/**
 * @extends CommonMethod<ListAttribute>
 * @syscap SystemCapability.ArkUI.ArkUI.Full
 * @form
 * @since 9
 */
/**
 * @extends CommonMethod<ListAttribute>
 * @syscap SystemCapability.ArkUI.ArkUI.Full
 * @crossplatform
 * @form
 * @since 10
 */
/**
 * @extends ScrollableCommonMethod<ListAttribute>
 * @syscap SystemCapability.ArkUI.ArkUI.Full
 * @crossplatform
 * @form
 * @atomicservice
 * @since 11
 */
declare class ListAttribute extends ScrollableCommonMethod<ListAttribute> {
    /**
     * Called when need to decide how much lanes the list will show.
     *
     * @param { number | LengthConstrain } value
     * @returns { ListAttribute }
     * @syscap SystemCapability.ArkUI.ArkUI.Full
     * @form
     * @since 9
     */
    /**
     * Called when need to decide how much lanes the list will show.
     *
     * @param { number | LengthConstrain } value
     * @param { Dimension } gutter
     * @returns { ListAttribute }
     * @syscap SystemCapability.ArkUI.ArkUI.Full
     * @crossplatform
     * @form
     * @since 10
     */
    /**
     * Called when need to decide how much lanes the list will show.
     *
     * @param { number | LengthConstrain } value
     * @param { Dimension } gutter
     * @returns { ListAttribute }
     * @syscap SystemCapability.ArkUI.ArkUI.Full
     * @crossplatform
     * @form
     * @atomicservice
     * @since 11
     */
    lanes(value: number | LengthConstrain, gutter?: Dimension): ListAttribute;
    /**
     * Called when need to decide how to align lanes in the direction of the cross axis.
     *
     * @param { ListItemAlign } value
     * @returns { ListAttribute }
     * @syscap SystemCapability.ArkUI.ArkUI.Full
     * @form
     * @since 9
     */
    /**
     * Called when need to decide how to align lanes in the direction of the cross axis.
     *
     * @param { ListItemAlign } value
     * @returns { ListAttribute }
     * @syscap SystemCapability.ArkUI.ArkUI.Full
     * @crossplatform
     * @form
     * @since 10
     */
    /**
     * Called when need to decide how to align lanes in the direction of the cross axis.
     *
     * @param { ListItemAlign } value
     * @returns { ListAttribute }
     * @syscap SystemCapability.ArkUI.ArkUI.Full
     * @crossplatform
     * @form
     * @atomicservice
     * @since 11
     */
    alignListItem(value: ListItemAlign): ListAttribute;
    /**
     * Called when the arrangement direction of the list component is set.
     *
     * @param { Axis } value
     * @returns { ListAttribute }
     * @syscap SystemCapability.ArkUI.ArkUI.Full
     * @since 7
     */
    /**
     * Called when the arrangement direction of the list component is set.
     *
     * @param { Axis } value
     * @returns { ListAttribute }
     * @syscap SystemCapability.ArkUI.ArkUI.Full
     * @form
     * @since 9
     */
    /**
     * Called when the arrangement direction of the list component is set.
     *
     * @param { Axis } value
     * @returns { ListAttribute }
     * @syscap SystemCapability.ArkUI.ArkUI.Full
     * @crossplatform
     * @form
     * @since 10
     */
    /**
     * Called when the arrangement direction of the list component is set.
     *
     * @param { Axis } value
     * @returns { ListAttribute }
     * @syscap SystemCapability.ArkUI.ArkUI.Full
     * @crossplatform
     * @form
     * @atomicservice
     * @since 11
     */
    listDirection(value: Axis): ListAttribute;
    /**
     * Called when the display mode of the side slider is set.
     *
     * @param { BarState } value
     * @returns { ListAttribute }
     * @syscap SystemCapability.ArkUI.ArkUI.Full
     * @since 7
     */
    /**
     * Called when the display mode of the side slider is set.
     *
     * @param { BarState } value
     * @returns { ListAttribute }
     * @syscap SystemCapability.ArkUI.ArkUI.Full
     * @form
     * @since 9
     */
    /**
     * Called when the display mode of the side slider is set.
     *
     * @param { BarState } value
     * @returns { ListAttribute }
     * @syscap SystemCapability.ArkUI.ArkUI.Full
     * @crossplatform
     * @form
     * @since 10
     */
    /**
     * Called when the display mode of the side slider is set.
     *
     * @param { BarState } value
     * @returns { ListAttribute }
     * @syscap SystemCapability.ArkUI.ArkUI.Full
     * @crossplatform
     * @form
     * @atomicservice
     * @since 11
     */
    scrollBar(value: BarState): ListAttribute;
    /**
     * Called when the sliding effect is set.
     *
     * @param { EdgeEffect } value
     * @returns { ListAttribute }
     * @syscap SystemCapability.ArkUI.ArkUI.Full
     * @since 7
     */
    /**
     * Called when the sliding effect is set.
     *
     * @param { EdgeEffect } value
     * @returns { ListAttribute }
     * @syscap SystemCapability.ArkUI.ArkUI.Full
     * @form
     * @since 9
     */
    /**
     * Called when the sliding effect is set.
     *
     * @param { EdgeEffect } value
     * @returns { ListAttribute }
     * @syscap SystemCapability.ArkUI.ArkUI.Full
     * @crossplatform
     * @form
     * @since 10
     */
    /**
     * Called when the sliding effect is set.
     *
     * @param { EdgeEffect } value
     * @param { EdgeEffectOptions } options
     * @returns { ListAttribute }
     * @syscap SystemCapability.ArkUI.ArkUI.Full
     * @crossplatform
     * @form
     * @atomicservice
     * @since 11
     */
    edgeEffect(value: EdgeEffect, options?: EdgeEffectOptions): ListAttribute;
    /**
     * Called when need to decide contentStartOffset the list will show.
     * @param { number } value - the value Of startOffset.
     * @returns { ListAttribute } the attribute of the list.
     * @syscap SystemCapability.ArkUI.ArkUI.Full
     * @crossplatform
     * @since 11
     */
    /**
     * Called when need to decide contentStartOffset the list will show.
     * @param { number } value - the value Of startOffset.
     * @returns { ListAttribute } the attribute of the list.
     * @syscap SystemCapability.ArkUI.ArkUI.Full
     * @crossplatform
     * @atomicservice
     * @since 12
     */
    contentStartOffset(value: number): ListAttribute;
    /**
     * Called when need to decide contentEndOffset the list will show.
     * @param { number } value - the value Of endOffset.
     * @returns { ListAttribute } the attribute of the list.
     * @syscap SystemCapability.ArkUI.ArkUI.Full
     * @crossplatform
     * @since 11
     */
    /**
     * Called when need to decide contentEndOffset the list will show.
     * @param { number } value - the value Of endOffset.
     * @returns { ListAttribute } the attribute of the list.
     * @syscap SystemCapability.ArkUI.ArkUI.Full
     * @crossplatform
     * @atomicservice
     * @since 12
     */
    contentEndOffset(value: number): ListAttribute;
    /**
     * Called when the ListItem split line style is set.
     *
     * @param { object | null } value
     * @returns { ListAttribute }
     * @syscap SystemCapability.ArkUI.ArkUI.Full
     * @since 7
     */
    /**
     * Called when the ListItem split line style is set.
     *
     * @param { {strokeWidth: Length;color?: ResourceColor;startMargin?: Length;endMargin?: Length;} | null } value
     * @returns { ListAttribute }
     * @syscap SystemCapability.ArkUI.ArkUI.Full
     * @form
     * @since 9
     */
    /**
     * Called when the ListItem split line style is set.
     *
     * @param { {strokeWidth: Length;color?: ResourceColor;startMargin?: Length;endMargin?: Length;} | null } value
     * @returns { ListAttribute }
     * @syscap SystemCapability.ArkUI.ArkUI.Full
     * @crossplatform
     * @form
     * @since 10
     */
    /**
     * Called when the ListItem split line style is set.
     *
     * @param { {strokeWidth: Length;color?: ResourceColor;startMargin?: Length;endMargin?: Length;} | null } value
     * @returns { ListAttribute }
     * @syscap SystemCapability.ArkUI.ArkUI.Full
     * @crossplatform
     * @form
     * @atomicservice
     * @since 11
     */
    divider(value: {
        strokeWidth: Length;
        color?: ResourceColor;
        startMargin?: Length;
        endMargin?: Length;
    } | null): ListAttribute;
    /**
     * Called when judging whether it is in editable mode.
     *
     * @param { boolean } value
     * @returns { ListAttribute }
     * @syscap SystemCapability.ArkUI.ArkUI.Full
     * @since 7
     * @deprecated since 9
     */
    editMode(value: boolean): ListAttribute;
    /**
     * Called when judging whether it is multiSelectable.
     *
     * @param { boolean } value
     * @returns { ListAttribute }
     * @syscap SystemCapability.ArkUI.ArkUI.Full
     * @since 8
     */
    /**
     * Called when judging whether it is multiSelectable.
     *
     * @param { boolean } value
     * @returns { ListAttribute }
     * @syscap SystemCapability.ArkUI.ArkUI.Full
     * @form
     * @since 9
     */
    /**
     * Called when judging whether it is multiSelectable.
     *
     * @param { boolean } value
     * @returns { ListAttribute }
     * @syscap SystemCapability.ArkUI.ArkUI.Full
     * @crossplatform
     * @form
     * @since 10
     */
    /**
     * Called when judging whether it is multiSelectable.
     *
     * @param { boolean } value
     * @returns { ListAttribute }
     * @syscap SystemCapability.ArkUI.ArkUI.Full
     * @crossplatform
     * @form
     * @atomicservice
     * @since 11
     */
    multiSelectable(value: boolean): ListAttribute;
    /**
     * Called when the minimum number of list item caches is set for long list deferred loading.
     *
     * @param { number } value
     * @returns { ListAttribute }
     * @syscap SystemCapability.ArkUI.ArkUI.Full
     * @since 7
     */
    /**
     * Called when the minimum number of list item caches is set for long list deferred loading.
     *
     * @param { number } value
     * @returns { ListAttribute }
     * @syscap SystemCapability.ArkUI.ArkUI.Full
     * @form
     * @since 9
     */
    /**
     * Called when the minimum number of list item caches is set for long list deferred loading.
     *
     * @param { number } value
     * @returns { ListAttribute }
     * @syscap SystemCapability.ArkUI.ArkUI.Full
     * @crossplatform
     * @form
     * @since 10
     */
    /**
     * Called when the minimum number of list item caches is set for long list deferred loading.
     *
     * @param { number } value
     * @returns { ListAttribute }
     * @syscap SystemCapability.ArkUI.ArkUI.Full
     * @crossplatform
     * @form
     * @atomicservice
     * @since 11
     */
    cachedCount(value: number): ListAttribute;
    /**
     * Called when setting whether to enable chain linkage dynamic effect.
     *
     * @param { boolean } value
     * @returns { ListAttribute }
     * @syscap SystemCapability.ArkUI.ArkUI.Full
     * @since 7
     */
    /**
     * Called when setting whether to enable chain linkage dynamic effect.
     *
     * @param { boolean } value
     * @returns { ListAttribute }
     * @syscap SystemCapability.ArkUI.ArkUI.Full
     * @form
     * @since 9
     */
    /**
     * Called when setting whether to enable chain linkage dynamic effect.
     *
     * @param { boolean } value
     * @returns { ListAttribute }
     * @syscap SystemCapability.ArkUI.ArkUI.Full
     * @crossplatform
     * @form
     * @since 10
     */
    /**
     * Called when setting whether to enable chain linkage dynamic effect.
     *
     * @param { boolean } value
     * @returns { ListAttribute }
     * @syscap SystemCapability.ArkUI.ArkUI.Full
     * @crossplatform
     * @form
     * @atomicservice
     * @since 11
     */
    chainAnimation(value: boolean): ListAttribute;
    /**
     * Called when header or footer of item group will be pinned.
     *
     * @param { StickyStyle } value
     * @returns { ListAttribute }
     * @syscap SystemCapability.ArkUI.ArkUI.Full
     * @form
     * @since 9
     */
    /**
     * Called when header or footer of item group will be pinned.
     *
     * @param { StickyStyle } value
     * @returns { ListAttribute }
     * @syscap SystemCapability.ArkUI.ArkUI.Full
     * @crossplatform
     * @form
     * @since 10
     */
    /**
     * Called when header or footer of item group will be pinned.
     *
     * @param { StickyStyle } value
     * @returns { ListAttribute }
     * @syscap SystemCapability.ArkUI.ArkUI.Full
     * @crossplatform
     * @form
     * @atomicservice
     * @since 11
     */
    sticky(value: StickyStyle): ListAttribute;
    /**
     * Called to set list item scroll end alignment effect.
     *
     * @param { ScrollSnapAlign } value - options of the list alignment effect.
     * @returns { ListAttribute } the attribute of the list.
     * @syscap SystemCapability.ArkUI.ArkUI.Full
     * @since 10
     */
    /**
     * Called to set list item scroll end alignment effect.
     *
     * @param { ScrollSnapAlign } value - options of the list alignment effect.
     * @returns { ListAttribute } the attribute of the list.
     * @syscap SystemCapability.ArkUI.ArkUI.Full
     * @crossplatform
     * @atomicservice
     * @since 11
     */
    scrollSnapAlign(value: ScrollSnapAlign): ListAttribute;
    /**
     * Called to setting the nested scroll options.
     *
     * @param { NestedScrollOptions } value - options for nested scrolling.
     * @returns { ListAttribute } the attribute of the list.
     * @syscap SystemCapability.ArkUI.ArkUI.Full
     * @since 10
     */
    /**
     * Called to setting the nested scroll options.
     *
     * @param { NestedScrollOptions } value - options for nested scrolling.
     * @returns { ListAttribute } the attribute of the list.
     * @syscap SystemCapability.ArkUI.ArkUI.Full
     * @crossplatform
     * @atomicservice
     * @since 11
     */
    nestedScroll(value: NestedScrollOptions): ListAttribute;
    /**
     * Called when setting whether to enable scroll by gesture or mouse.
     *
     * @param { boolean } value
     * @returns { ListAttribute } The attribute of the list
     * @syscap SystemCapability.ArkUI.ArkUI.Full
     * @crossplatform
     * @since 10
     */
    /**
     * Called when setting whether to enable scroll by gesture or mouse.
     *
     * @param { boolean } value
     * @returns { ListAttribute } The attribute of the list
     * @syscap SystemCapability.ArkUI.ArkUI.Full
     * @crossplatform
     * @atomicservice
     * @since 11
     */
    enableScrollInteraction(value: boolean): ListAttribute;
    /**
     * Called to setting the friction.
     *
     * @param { number | Resource } value - options for scrolling friction.
     * @returns { ListAttribute } the attribute of the list.
     * @syscap SystemCapability.ArkUI.ArkUI.Full
     * @crossplatform
     * @since 10
     */
    /**
     * Called to setting the friction.
     *
     * @param { number | Resource } value - options for scrolling friction.
     * @returns { ListAttribute } the attribute of the list.
     * @syscap SystemCapability.ArkUI.ArkUI.Full
     * @crossplatform
     * @atomicservice
     * @since 11
     */
    friction(value: number | Resource): ListAttribute;
    /**
     * Set children main size for List.
     *
     * @param { ChildrenMainSize } value - children main size for List
     * @returns { ListAttribute } the attribute of the list.
     * @syscap SystemCapability.ArkUI.ArkUI.Full
     * @crossplatform
     * @atomicservice
     * @since 12
     */
    childrenMainSize(value: ChildrenMainSize): ListAttribute;
    /**
     * Set maintain visible content position List.
     *
     * @param { boolean } enabled - maintain visible content position.
     * @returns { ListAttribute } the attribute of the list.
     * @syscap SystemCapability.ArkUI.ArkUI.Full
     * @crossplatform
     * @atomicservice
     * @since 12
     */
    maintainVisibleContentPosition(enabled: boolean): ListAttribute;
    /**
     * Called when the offset and status callback of the slide are set.
     *
     * @param { function } event
     * @returns { ListAttribute }
     * @syscap SystemCapability.ArkUI.ArkUI.Full
     * @since 7
     */
    /**
     * Called when the offset and status callback of the slide are set.
     *
     * @param { function } event
     * @returns { ListAttribute }
     * @syscap SystemCapability.ArkUI.ArkUI.Full
     * @form
     * @since 9
     */
    /**
     * Called when the offset and status callback of the slide are set.
     *
     * @param { function } event
     * @returns { ListAttribute }
     * @syscap SystemCapability.ArkUI.ArkUI.Full
     * @crossplatform
     * @form
     * @since 10
     */
    /**
     * Called when the offset and status callback of the slide are set.
     *
     * @param { function } event
     * @returns { ListAttribute }
     * @syscap SystemCapability.ArkUI.ArkUI.Full
     * @crossplatform
     * @form
     * @atomicservice
     * @since 11
     * @deprecated since 12
     * @useinstead common.ScrollableCommonMethod#onDidScroll
     */
    onScroll(event: (scrollOffset: number, scrollState: ScrollState) => void): ListAttribute;
    /**
     * Called when the start and end positions of the display change.
     *
     * @param { function } event
     * @returns { ListAttribute }
     * @syscap SystemCapability.ArkUI.ArkUI.Full
     * @since 7
     */
    /**
     * Called when the start and end positions of the display change.
     *
     * @param { function } event
     * @returns { ListAttribute }
     * @syscap SystemCapability.ArkUI.ArkUI.Full
     * @form
     * @since 9
     */
    /**
     * Called when the start and end positions of the display change.
     *
     * @param { function } event
     * @returns { ListAttribute }
     * @syscap SystemCapability.ArkUI.ArkUI.Full
     * @crossplatform
     * @form
     * @since 10
     */
    /**
     * Called when the start and end positions of the display change.
     *
     * @param { function } event
     * @returns { ListAttribute }
     * @syscap SystemCapability.ArkUI.ArkUI.Full
     * @crossplatform
     * @form
     * @atomicservice
     * @since 11
     */
    onScrollIndex(event: (start: number, end: number, center: number) => void): ListAttribute;
    /**
     * Called when the list visible content changes.
     *
     * @param { OnScrollVisibleContentChangeCallback } handler - Callback of Scroll Visible.
     * @returns { ListAttribute }
     * @syscap SystemCapability.ArkUI.ArkUI.Full
     * @crossplatform
     * @atomicservice
     * @since 12
     */
    onScrollVisibleContentChange(handler: OnScrollVisibleContentChangeCallback): ListAttribute;
    /**
     * Called when the list begins to arrive.
     *
     * @param { function } event
     * @returns { ListAttribute }
     * @syscap SystemCapability.ArkUI.ArkUI.Full
     * @since 7
     */
    /**
     * Called when the list begins to arrive.
     *
     * @param { function } event
     * @returns { ListAttribute }
     * @syscap SystemCapability.ArkUI.ArkUI.Full
     * @form
     * @since 9
     */
    /**
     * Called when the list begins to arrive.
     *
     * @param { function } event
     * @returns { ListAttribute }
     * @syscap SystemCapability.ArkUI.ArkUI.Full
     * @crossplatform
     * @form
     * @since 10
     */
    /**
     * Called when the list begins to arrive.
     *
     * @param { function } event
     * @returns { ListAttribute }
     * @syscap SystemCapability.ArkUI.ArkUI.Full
     * @crossplatform
     * @form
     * @atomicservice
     * @since 11
     */
    onReachStart(event: () => void): ListAttribute;
    /**
     * Called when the list reaches the end.
     *
     * @param { function } event
     * @returns { ListAttribute }
     * @syscap SystemCapability.ArkUI.ArkUI.Full
     * @since 7
     */
    /**
     * Called when the list reaches the end.
     *
     * @param { function } event
     * @returns { ListAttribute }
     * @syscap SystemCapability.ArkUI.ArkUI.Full
     * @form
     * @since 9
     */
    /**
     * Called when the list reaches the end.
     *
     * @param { function } event
     * @returns { ListAttribute }
     * @syscap SystemCapability.ArkUI.ArkUI.Full
     * @crossplatform
     * @form
     * @since 10
     */
    /**
     * Called when the list reaches the end.
     *
     * @param { function } event
     * @returns { ListAttribute }
     * @syscap SystemCapability.ArkUI.ArkUI.Full
     * @crossplatform
     * @form
     * @atomicservice
     * @since 11
     */
    onReachEnd(event: () => void): ListAttribute;
    /**
     * Called when the slider start.
     *
     * @param { function } event
     * @returns { ListAttribute }
     * @syscap SystemCapability.ArkUI.ArkUI.Full
     * @form
     * @since 9
     */
    /**
     * Called when the slider start.
     *
     * @param { function } event
     * @returns { ListAttribute }
     * @syscap SystemCapability.ArkUI.ArkUI.Full
     * @crossplatform
     * @form
     * @since 10
     */
    /**
     * Called when the slider start.
     *
     * @param { function } event
     * @returns { ListAttribute }
     * @syscap SystemCapability.ArkUI.ArkUI.Full
     * @crossplatform
     * @form
     * @atomicservice
     * @since 11
     */
    onScrollStart(event: () => void): ListAttribute;
    /**
     * Called when the slider stops.
     *
     * @param { function } event
     * @returns { ListAttribute }
     * @syscap SystemCapability.ArkUI.ArkUI.Full
     * @since 7
     */
    /**
     * Called when the slider stops.
     *
     * @param { function } event
     * @returns { ListAttribute }
     * @syscap SystemCapability.ArkUI.ArkUI.Full
     * @form
     * @since 9
     */
    /**
     * Called when the slider stops.
     *
     * @param { function } event
     * @returns { ListAttribute }
     * @syscap SystemCapability.ArkUI.ArkUI.Full
     * @crossplatform
     * @form
     * @since 10
     */
    /**
     * Called when the slider stops.
     *
     * @param { function } event
     * @returns { ListAttribute }
     * @syscap SystemCapability.ArkUI.ArkUI.Full
     * @crossplatform
     * @form
     * @atomicservice
     * @since 11
     */
    onScrollStop(event: () => void): ListAttribute;
    /**
     * Called when a list item is deleted.
     *
     * @param { function } event
     * @returns { ListAttribute }
     * @syscap SystemCapability.ArkUI.ArkUI.Full
     * @since 7
     * @deprecated since 9
     */
    onItemDelete(event: (index: number) => boolean): ListAttribute;
    /**
     * Called when a list item is moved.
     *
     * @param { function } event
     * @returns { ListAttribute }
     * @syscap SystemCapability.ArkUI.ArkUI.Full
     * @since 7
     */
    /**
     * Called when a list item is moved.
     *
     * @param { function } event
     * @returns { ListAttribute }
     * @syscap SystemCapability.ArkUI.ArkUI.Full
     * @crossplatform
     * @since 10
     */
    /**
     * Called when a list item is moved.
     *
     * @param { function } event
     * @returns { ListAttribute }
     * @syscap SystemCapability.ArkUI.ArkUI.Full
     * @crossplatform
     * @atomicservice
     * @since 11
     */
    onItemMove(event: (from: number, to: number) => boolean): ListAttribute;
    /**
     * After a listener is bound, the component can be dragged. After the drag occurs, a callback is triggered.
     * (To be triggered, press and hold for 170 milliseconds (ms))
     *
     * @param { function } event
     * @returns { ListAttribute }
     * @syscap SystemCapability.ArkUI.ArkUI.Full
     * @since 8
     */
    /**
     * After a listener is bound, the component can be dragged. After the drag occurs, a callback is triggered.
     * (To be triggered, press and hold for 170 milliseconds (ms))
     *
     * @param { function } event
     * @returns { ListAttribute }
     * @syscap SystemCapability.ArkUI.ArkUI.Full
     * @crossplatform
     * @since 10
     */
    /**
     * After a listener is bound, the component can be dragged. After the drag occurs, a callback is triggered.
     * (To be triggered, press and hold for 170 milliseconds (ms))
     *
     * @param { function } event
     * @returns { ListAttribute }
     * @syscap SystemCapability.ArkUI.ArkUI.Full
     * @crossplatform
     * @atomicservice
     * @since 11
     */
    onItemDragStart(event: (event: ItemDragInfo, itemIndex: number) => ((() => any) | void)): ListAttribute;
    /**
     * After binding, a callback is triggered when the component is dragged to the range of the component.
     *
     * @param { function } event
     * @returns { ListAttribute }
     * @syscap SystemCapability.ArkUI.ArkUI.Full
     * @since 8
     */
    /**
     * After binding, a callback is triggered when the component is dragged to the range of the component.
     *
     * @param { function } event
     * @returns { ListAttribute }
     * @syscap SystemCapability.ArkUI.ArkUI.Full
     * @crossplatform
     * @since 10
     */
    /**
     * After binding, a callback is triggered when the component is dragged to the range of the component.
     *
     * @param { function } event
     * @returns { ListAttribute }
     * @syscap SystemCapability.ArkUI.ArkUI.Full
     * @crossplatform
     * @atomicservice
     * @since 11
     */
    onItemDragEnter(event: (event: ItemDragInfo) => void): ListAttribute;
    /**
     * After binding, a callback is triggered when the drag moves within the range of a placeable component.
     *
     * @param { function } event
     * @returns { ListAttribute }
     * @syscap SystemCapability.ArkUI.ArkUI.Full
     * @since 8
     */
    /**
     * After binding, a callback is triggered when the drag moves within the range of a placeable component.
     *
     * @param { function } event
     * @returns { ListAttribute }
     * @syscap SystemCapability.ArkUI.ArkUI.Full
     * @crossplatform
     * @since 10
     */
    /**
     * After binding, a callback is triggered when the drag moves within the range of a placeable component.
     *
     * @param { function } event
     * @returns { ListAttribute }
     * @syscap SystemCapability.ArkUI.ArkUI.Full
     * @crossplatform
     * @atomicservice
     * @since 11
     */
    onItemDragMove(event: (event: ItemDragInfo, itemIndex: number, insertIndex: number) => void): ListAttribute;
    /**
     * After binding, a callback is triggered when the component is dragged out of the component range.
     *
     * @param { function } event
     * @returns { ListAttribute }
     * @syscap SystemCapability.ArkUI.ArkUI.Full
     * @since 8
     */
    /**
     * After binding, a callback is triggered when the component is dragged out of the component range.
     *
     * @param { function } event
     * @returns { ListAttribute }
     * @syscap SystemCapability.ArkUI.ArkUI.Full
     * @crossplatform
     * @since 10
     */
    /**
     * After binding, a callback is triggered when the component is dragged out of the component range.
     *
     * @param { function } event
     * @returns { ListAttribute }
     * @syscap SystemCapability.ArkUI.ArkUI.Full
     * @crossplatform
     * @atomicservice
     * @since 11
     */
    onItemDragLeave(event: (event: ItemDragInfo, itemIndex: number) => void): ListAttribute;
    /**
     * The component bound to this event can be used as the drag release target.
     * This callback is triggered when the drag behavior is stopped within the scope of the component.
     *
     * @param { function } event
     * @returns { ListAttribute }
     * @syscap SystemCapability.ArkUI.ArkUI.Full
     * @since 8
     */
    /**
     * The component bound to this event can be used as the drag release target.
     * This callback is triggered when the drag behavior is stopped within the scope of the component.
     *
     * @param { function } event
     * @returns { ListAttribute }
     * @syscap SystemCapability.ArkUI.ArkUI.Full
     * @crossplatform
     * @since 10
     */
    /**
     * The component bound to this event can be used as the drag release target.
     * This callback is triggered when the drag behavior is stopped within the scope of the component.
     *
     * @param { function } event
     * @returns { ListAttribute }
     * @syscap SystemCapability.ArkUI.ArkUI.Full
     * @crossplatform
     * @atomicservice
     * @since 11
     */
    onItemDrop(event: (event: ItemDragInfo, itemIndex: number, insertIndex: number, isSuccess: boolean) => void): ListAttribute;
    /**
     * Called when scrolling begin each frame.
     *
     * @param { function } event
     * @returns { ListAttribute }
     * @syscap SystemCapability.ArkUI.ArkUI.Full
     * @form
     * @since 9
     */
    /**
     * Called when scrolling begin each frame.
     *
     * @param { function } event
     * @returns { ListAttribute }
     * @syscap SystemCapability.ArkUI.ArkUI.Full
     * @crossplatform
     * @form
     * @since 10
     */
    /**
     * Called when scrolling begin each frame.
     *
     * @param { function } event
     * @returns { ListAttribute }
     * @syscap SystemCapability.ArkUI.ArkUI.Full
     * @crossplatform
     * @form
     * @atomicservice
     * @since 11
     */
    onScrollFrameBegin(event: (offset: number, state: ScrollState) => {
        offsetRemain: number;
    }): ListAttribute;
}
/**
 * Defines List Component.
 *
 * @syscap SystemCapability.ArkUI.ArkUI.Full
 * @since 7
 */
/**
 * Defines List Component.
 *
 * @syscap SystemCapability.ArkUI.ArkUI.Full
 * @form
 * @since 9
 */
/**
 * Defines List Component.
 *
 * @syscap SystemCapability.ArkUI.ArkUI.Full
 * @crossplatform
 * @form
 * @since 10
 */
/**
 * Defines List Component.
 *
 * @syscap SystemCapability.ArkUI.ArkUI.Full
 * @crossplatform
 * @form
 * @atomicservice
 * @since 11
 */
declare const List: ListInterface;
/**
 * Defines List Component instance.
 *
 * @syscap SystemCapability.ArkUI.ArkUI.Full
 * @since 7
 */
/**
 * Defines List Component instance.
 *
 * @syscap SystemCapability.ArkUI.ArkUI.Full
 * @form
 * @since 9
 */
/**
 * Defines List Component instance.
 *
 * @syscap SystemCapability.ArkUI.ArkUI.Full
 * @crossplatform
 * @form
 * @since 10
 */
/**
 * Defines List Component instance.
 *
 * @syscap SystemCapability.ArkUI.ArkUI.Full
 * @crossplatform
 * @form
 * @atomicservice
 * @since 11
 */
declare const ListInstance: ListAttribute;
