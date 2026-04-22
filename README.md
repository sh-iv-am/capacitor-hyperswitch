# capacitor-hyperswitch

Hyperswitch SDK bindings for Capacitor Applications

## Install

To use npm

```bash
npm install capacitor-hyperswitch
````

To use yarn

```bash
yarn add capacitor-hyperswitch
```

Sync native files

```bash
npx cap sync
```

## API

<docgen-index>

* [`init(...)`](#init)
* [`elements(...)`](#elements)
* [`createElement(...)`](#createelement)
* [`updateIntent(...)`](#updateintent)
* [`getCustomerSavedPaymentMethods()`](#getcustomersavedpaymentmethods)
* [`getCustomerSavedPaymentMethodData(...)`](#getcustomersavedpaymentmethoddata)
* [`getCustomerDefaultSavedPaymentMethodData(...)`](#getcustomerdefaultsavedpaymentmethoddata)
* [`getCustomerLastUsedPaymentMethodData(...)`](#getcustomerlastusedpaymentmethoddata)
* [`confirmWithCustomerDefaultPaymentMethod(...)`](#confirmwithcustomerdefaultpaymentmethod)
* [`confirmWithCustomerLastUsedPaymentMethod(...)`](#confirmwithcustomerlastusedpaymentmethod)
* [`confirmPayment(...)`](#confirmpayment)
* [`initPaymentSession(...)`](#initpaymentsession)
* [`presentPaymentSheet(...)`](#presentpaymentsheet)
* [`elementOn(...)`](#elementon)
* [`elementCollapse()`](#elementcollapse)
* [`elementBlur()`](#elementblur)
* [`elementUpdate(...)`](#elementupdate)
* [`elementDestroy()`](#elementdestroy)
* [`elementUnmount()`](#elementunmount)
* [`elementMount(...)`](#elementmount)
* [`elementFocus()`](#elementfocus)
* [`elementClear()`](#elementclear)
* [`addListener('paymentEvent', ...)`](#addlistenerpaymentevent-)
* [Interfaces](#interfaces)
* [Type Aliases](#type-aliases)

</docgen-index>

<docgen-api>
<!--Update the source file JSDoc comments and rerun docgen to update the docs below-->

### init(...)

```typescript
init(config: HyperConfig) => Promise<void>
```

| Param        | Type                                                |
| ------------ | --------------------------------------------------- |
| **`config`** | <code><a href="#hyperconfig">HyperConfig</a></code> |

--------------------


### elements(...)

```typescript
elements(options: { elementsOptions: JSONValue; }) => Promise<{ handlerId: string; }>
```

| Param         | Type                                                                  |
| ------------- | --------------------------------------------------------------------- |
| **`options`** | <code>{ elementsOptions: <a href="#jsonvalue">JSONValue</a>; }</code> |

**Returns:** <code>Promise&lt;{ handlerId: string; }&gt;</code>

--------------------


### createElement(...)

```typescript
createElement(options: { type: string; createOptions: JSONValue; }) => Promise<void>
```

| Param         | Type                                                                              |
| ------------- | --------------------------------------------------------------------------------- |
| **`options`** | <code>{ type: string; createOptions: <a href="#jsonvalue">JSONValue</a>; }</code> |

--------------------


### updateIntent(...)

```typescript
updateIntent(options: { sdkAuthorization: string; }) => Promise<UpdateIntentResult>
```

| Param         | Type                                       |
| ------------- | ------------------------------------------ |
| **`options`** | <code>{ sdkAuthorization: string; }</code> |

**Returns:** <code>Promise&lt;<a href="#updateintentresult">UpdateIntentResult</a>&gt;</code>

--------------------


### getCustomerSavedPaymentMethods()

```typescript
getCustomerSavedPaymentMethods() => Promise<{ handlerId: string; }>
```

**Returns:** <code>Promise&lt;{ handlerId: string; }&gt;</code>

--------------------


### getCustomerSavedPaymentMethodData(...)

```typescript
getCustomerSavedPaymentMethodData(options: { handlerId: string; }) => Promise<JSONValue>
```

| Param         | Type                                |
| ------------- | ----------------------------------- |
| **`options`** | <code>{ handlerId: string; }</code> |

**Returns:** <code>Promise&lt;<a href="#jsonvalue">JSONValue</a>&gt;</code>

--------------------


### getCustomerDefaultSavedPaymentMethodData(...)

```typescript
getCustomerDefaultSavedPaymentMethodData(options: { handlerId: string; }) => Promise<JSONValue>
```

| Param         | Type                                |
| ------------- | ----------------------------------- |
| **`options`** | <code>{ handlerId: string; }</code> |

**Returns:** <code>Promise&lt;<a href="#jsonvalue">JSONValue</a>&gt;</code>

--------------------


### getCustomerLastUsedPaymentMethodData(...)

```typescript
getCustomerLastUsedPaymentMethodData(options: { handlerId: string; }) => Promise<JSONValue>
```

| Param         | Type                                |
| ------------- | ----------------------------------- |
| **`options`** | <code>{ handlerId: string; }</code> |

**Returns:** <code>Promise&lt;<a href="#jsonvalue">JSONValue</a>&gt;</code>

--------------------


### confirmWithCustomerDefaultPaymentMethod(...)

```typescript
confirmWithCustomerDefaultPaymentMethod(options: { handlerId: string; }) => Promise<PaymentResult>
```

| Param         | Type                                |
| ------------- | ----------------------------------- |
| **`options`** | <code>{ handlerId: string; }</code> |

**Returns:** <code>Promise&lt;<a href="#paymentresult">PaymentResult</a>&gt;</code>

--------------------


### confirmWithCustomerLastUsedPaymentMethod(...)

```typescript
confirmWithCustomerLastUsedPaymentMethod(options: { handlerId: string; }) => Promise<PaymentResult>
```

| Param         | Type                                |
| ------------- | ----------------------------------- |
| **`options`** | <code>{ handlerId: string; }</code> |

**Returns:** <code>Promise&lt;<a href="#paymentresult">PaymentResult</a>&gt;</code>

--------------------


### confirmPayment(...)

```typescript
confirmPayment(options: { confirmParams: JSONValue; }) => Promise<PaymentResult>
```

| Param         | Type                                                                |
| ------------- | ------------------------------------------------------------------- |
| **`options`** | <code>{ confirmParams: <a href="#jsonvalue">JSONValue</a>; }</code> |

**Returns:** <code>Promise&lt;<a href="#paymentresult">PaymentResult</a>&gt;</code>

--------------------


### initPaymentSession(...)

```typescript
initPaymentSession(options: { paymentSessionOptions: JSONValue; }) => Promise<void>
```

| Param         | Type                                                                        |
| ------------- | --------------------------------------------------------------------------- |
| **`options`** | <code>{ paymentSessionOptions: <a href="#jsonvalue">JSONValue</a>; }</code> |

--------------------


### presentPaymentSheet(...)

```typescript
presentPaymentSheet(options: { sheetOptions: JSONValue; }) => Promise<PaymentResult>
```

| Param         | Type                                                               |
| ------------- | ------------------------------------------------------------------ |
| **`options`** | <code>{ sheetOptions: <a href="#jsonvalue">JSONValue</a>; }</code> |

**Returns:** <code>Promise&lt;<a href="#paymentresult">PaymentResult</a>&gt;</code>

--------------------


### elementOn(...)

```typescript
elementOn(options: { event: string; }) => Promise<void>
```

| Param         | Type                            |
| ------------- | ------------------------------- |
| **`options`** | <code>{ event: string; }</code> |

--------------------


### elementCollapse()

```typescript
elementCollapse() => Promise<void>
```

--------------------


### elementBlur()

```typescript
elementBlur() => Promise<void>
```

--------------------


### elementUpdate(...)

```typescript
elementUpdate(options: { updateOptions: JSONValue; }) => Promise<void>
```

| Param         | Type                                                                |
| ------------- | ------------------------------------------------------------------- |
| **`options`** | <code>{ updateOptions: <a href="#jsonvalue">JSONValue</a>; }</code> |

--------------------


### elementDestroy()

```typescript
elementDestroy() => Promise<void>
```

--------------------


### elementUnmount()

```typescript
elementUnmount() => Promise<void>
```

--------------------


### elementMount(...)

```typescript
elementMount(options: { selector: string; }) => Promise<void>
```

| Param         | Type                               |
| ------------- | ---------------------------------- |
| **`options`** | <code>{ selector: string; }</code> |

--------------------


### elementFocus()

```typescript
elementFocus() => Promise<void>
```

--------------------


### elementClear()

```typescript
elementClear() => Promise<void>
```

--------------------


### addListener('paymentEvent', ...)

```typescript
addListener(event: 'paymentEvent', handler: (data: PaymentEventData) => void) => Promise<{ remove: () => Promise<void>; }>
```

Subscribe to native widget events emitted by PaymentElement or CvcWidget.
The handler receives a <a href="#paymenteventdata">PaymentEventData</a> object with `type` and `payload`.

Use `element.on(eventType, handler)` in the JS wrapper layer instead of
calling this directly.

| Param         | Type                                                                             |
| ------------- | -------------------------------------------------------------------------------- |
| **`event`**   | <code>'paymentEvent'</code>                                                      |
| **`handler`** | <code>(data: <a href="#paymenteventdata">PaymentEventData</a>) =&gt; void</code> |

**Returns:** <code>Promise&lt;{ remove: () =&gt; Promise&lt;void&gt;; }&gt;</code>

--------------------


### Interfaces


#### HyperConfig

| Prop                         | Type                                                  |
| ---------------------------- | ----------------------------------------------------- |
| **`publishableKey`**         | <code>string</code>                                   |
| **`profileId`**              | <code>string</code>                                   |
| **`platformPublishableKey`** | <code>string</code>                                   |
| **`customConfig`**           | <code><a href="#customconfig">CustomConfig</a></code> |


#### CustomConfig

| Prop                          | Type                |
| ----------------------------- | ------------------- |
| **`customEndpoint`**          | <code>string</code> |
| **`customBackendEndpoint`**   | <code>string</code> |
| **`customLoggingEndpoint`**   | <code>string</code> |
| **`customAssetEndpoint`**     | <code>string</code> |
| **`customSDKConfigEndpoint`** | <code>string</code> |
| **`customAirborneEndpoint`**  | <code>string</code> |


#### UpdateIntentResult

| Prop                 | Type                | Description                                     |
| -------------------- | ------------------- | ----------------------------------------------- |
| **`type`**           | <code>string</code> | "success" \| "totalFailure" \| "partialFailure" |
| **`message`**        | <code>string</code> |                                                 |
| **`failedCount`**    | <code>number</code> |                                                 |
| **`succeededCount`** | <code>number</code> |                                                 |


#### PaymentResult

| Prop          | Type                                               |
| ------------- | -------------------------------------------------- |
| **`type`**    | <code>'completed' \| 'canceled' \| 'failed'</code> |
| **`message`** | <code>string</code>                                |


#### PaymentEventData

Native event emitted by PaymentElement or CvcWidget.

`type` values:
  PaymentElement: "FORM_STATUS" | "PAYMENT_METHOD_STATUS" |
                  "PAYMENT_METHOD_INFO_CARD" | "PAYMENT_METHOD_INFO_BILLING_ADDRESS"
  CvcWidget:      "CVC_STATUS"

`payload` is the raw key/value data from the SDK (values serialised to strings on Android).

| Prop          | Type                                                            |
| ------------- | --------------------------------------------------------------- |
| **`type`**    | <code>string</code>                                             |
| **`payload`** | <code><a href="#record">Record</a>&lt;string, string&gt;</code> |


### Type Aliases


#### JSONValue

<code><a href="#record">Record</a>&lt;string, unknown&gt;</code>


#### Record

Construct a type with a set of properties K of type T

<code>{ [P in K]: T; }</code>

</docgen-api>
