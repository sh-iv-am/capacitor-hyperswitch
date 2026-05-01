import { Capacitor } from '@capacitor/core';
import { Hyperswitch } from '@juspay-tech/capacitor-hyperswitch';

const SERVER_URL =
  Capacitor.getPlatform() === 'android'
    ? 'http://10.0.2.2:5252'
    : 'http://localhost:5252';

// ── Shared state ─────────────────────────────────────────────────────────────

let fetchedData = null;       // { publishableKey, sdkAuthorization, paymentId }

// Flow 1
let flow1Session = null;      // InitPaymentSession
let flow1Handler = null;      // PaymentSessionHandler (from getCustomerSavedPaymentMethods)
let cvcWidget = null;

// Flow 2
let elementsSession = null;   // Elements
let paymentElement = null;

// ── Helpers ──────────────────────────────────────────────────────────────────

function out(id, text) {
  document.getElementById(id).textContent = text;
}

function resultText(r) {
  return `type: ${r.type}${r.message ? '\nmessage: ' + r.message : ''}`;
}

// ── Step 1: Fetch ─────────────────────────────────────────────────────────────

window.fetchPaymentIntent = async () => {
  out('fetchOutput', 'Fetching…');
  try {
    const res = await fetch(`${SERVER_URL}/create-payment-intent`);
    if (!res.ok) throw new Error((await res.json()).error ?? `HTTP ${res.status}`);
    fetchedData = await res.json();
    out('fetchOutput',
      `publishableKey: ${fetchedData.publishableKey}\n` +
      `sdkAuthorization: ${fetchedData.sdkAuthorization}\n` +
      `paymentId: ${fetchedData.paymentId ?? '(none)'}`
    );
  } catch (err) {
    fetchedData = null;
    out('fetchOutput', 'Error: ' + err.message);
  }
};

// ── Flow 1 — initPaymentSession ───────────────────────────────────────────────

window.initPaymentSession = async () => {
  if (!fetchedData) { out('initSessionOutput', 'Fetch first.'); return; }
  out('initSessionOutput', 'Initializing…');
  try {
    const session = Hyperswitch.init({ publishableKey: fetchedData.publishableKey });
    flow1Session = await session.initPaymentSession({ sdkAuthorization: fetchedData.sdkAuthorization });
    flow1Handler = null; // reset handler when re-initializing
    out('initSessionOutput', 'initPaymentSession ready.');
  } catch (err) {
    out('initSessionOutput', 'Error: ' + err.message);
  }
};

// Path A: presentPaymentSheet

window.presentSheet = async () => {
  if (!flow1Session) { out('sheetOutput', 'Call initPaymentSession first.'); return; }
  out('sheetOutput', 'Presenting…');
  try {
    const result = await flow1Session.presentPaymentSheet({
      appearance: {
        colors: {
          light: {
            primary: '#D32F2F',
            background: '#FAFAFA',
            componentBackground: '#FFFFFF',
            componentBorder: '#E0E0E0',
            componentDivider: '#EEEEEE',
            componentText: '#212121',
            primaryText: '#212121',
            secondaryText: '#757575',
            placeholderText: '#BDBDBD',
            icon: '#757575',
            error: '#D32F2F',
            loaderBackground: '#FFEBEE',
            loaderForeground: '#D32F2F'
          },
          dark: {
            primary: '#EF5350',
            background: '#121212',
            componentBackground: '#2C2C2C',
            componentBorder: '#424242',
            componentDivider: '#333333',
            componentText: '#EEEEEE',
            primaryText: '#EEEEEE',
            secondaryText: '#BDBDBD',
            placeholderText: '#757575',
            icon: '#BDBDBD',
            error: '#EF5350',
            loaderBackground: '#3E2723',
            loaderForeground: '#EF5350'
          }
        },
        shapes: {
          borderRadius: 12,
          borderWidth: 1
        },
         primaryButton: {
          shapes: {
            borderRadius: 12
          },
          colors: {
            light: {
              background: '#D32F2F',
              text: '#FFFFFF',
              border: '#D32F2F'
            },
            dark: {
              background: '#EF5350',
              text: '#FFFFFF',
              border: '#EF5350'
            }
          }
        },
        theme: 'Default'
      },
      primaryButtonLabel: 'Pay Now',
      merchantDisplayName: 'Hyperswitch Store',
      placeholder: {
        cardNumber: '4242 4242 4242 4242',
        expiryDate: 'MM / YY',
        cvv: 'CVC'
      }
    });
    out('sheetOutput', resultText(result));
  } catch (err) {
    out('sheetOutput', 'Error: ' + err.message);
  }
};

// Path B: saved methods

window.getCustomerSavedPaymentMethods = async () => {
  if (!flow1Session) { out('savedMethodsOutput', 'Call initPaymentSession first.'); return; }
  out('savedMethodsOutput', 'Fetching saved methods…');
  try {
    flow1Handler = await flow1Session.getCustomerSavedPaymentMethods();
    out('savedMethodsOutput', 'Handler ready (id: ' + flow1Handler.handlerId + ')');
  } catch (err) {
    out('savedMethodsOutput', 'Error: ' + err.message);
  }
};

window.mountCvcWidget = () => {
  if (!flow1Session) { out('savedMethodsOutput', 'Call initPaymentSession first.'); return; }
  if (!elementsSession) { out('savedMethodsOutput', 'CvcWidget requires Elements session — call elements() first (Flow 2).'); return; }
  
  // Create CVC widget WITH configuration options
  cvcWidget = elementsSession.create({ 
    type: 'cvcWidget',

    options: {
      appearance: {
        colors: {
          light: {
            primary: '#D32F2F',
            componentBackground: '#FFFFFF',
            componentBorder: '#E0E0E0',
            componentText: '#212121',
            placeholderText: '#BDBDBD',
            error: '#D32F2F'
          },
          dark: {
            primary: '#EF5350',
            componentBackground: '#2C2C2C',
            componentBorder: '#424242',
            componentText: '#EEEEEE',
            placeholderText: '#757575',
            error: '#EF5350'
          }
        },
        shapes: {
          borderRadius: 12,
          borderWidth: 1
        },
        font: {
          scale: 1.0
        }
      },
      placeholder: 'cvc123'
    }
  });
  
  cvcWidget.mount('#cvc-widget');
  
  // Add event listener to show it's working
  cvcWidget.on('change', (event) => {
    console.log('CVC Widget event:', JSON.stringify(event));
  });
  
  out('savedMethodsOutput', 'CVC Widget mounted with configuration');
};

window.unmountCvcWidget = () => {
  cvcWidget?.unmount();
  cvcWidget = null;
};

window.getLastUsedMethod = async () => {
  if (!flow1Handler) { out('savedDataOutput', 'Call getCustomerSavedPaymentMethods first.'); return; }
  try {
    const data = await flow1Handler.getCustomerLastUsedPaymentMethodData();
    out('savedDataOutput', 'Last used:\n' + JSON.stringify(data, null, 2));
  } catch (err) {
    out('savedDataOutput', 'Error: ' + err.message);
  }
};

window.getDefaultMethod = async () => {
  if (!flow1Handler) { out('savedDataOutput', 'Call getCustomerSavedPaymentMethods first.'); return; }
  try {
    const data = await flow1Handler.getCustomerDefaultSavedPaymentMethodData();
    out('savedDataOutput', 'Default:\n' + JSON.stringify(data, null, 2));
  } catch (err) {
    out('savedDataOutput', 'Error: ' + err.message);
  }
};

window.confirmWithLastUsed = async () => {
  if (!flow1Handler) { out('confirmSavedOutput', 'Call getCustomerSavedPaymentMethods first.'); return; }
  out('confirmSavedOutput', 'Confirming with last used…');
  try {
    const result = await flow1Handler.confirmWithCustomerLastUsedPaymentMethod();
    out('confirmSavedOutput', resultText(result));
  } catch (err) {
    out('confirmSavedOutput', 'Error: ' + err.message);
  }
};

window.confirmWithDefault = async () => {
  if (!flow1Handler) { out('confirmSavedOutput', 'Call getCustomerSavedPaymentMethods first.'); return; }
  out('confirmSavedOutput', 'Confirming with default…');
  try {
    const result = await flow1Handler.confirmWithCustomerDefaultPaymentMethod();
    out('confirmSavedOutput', resultText(result));
  } catch (err) {
    out('confirmSavedOutput', 'Error: ' + err.message);
  }
};

// ── Flow 2 — Elements ─────────────────────────────────────────────────────────

window.initElements = async () => {
  if (!fetchedData) { out('elementsOutput', 'Fetch first.'); return; }
  out('elementsOutput', 'Creating Elements session…');
  try {
    const session = Hyperswitch.init({ publishableKey: fetchedData.publishableKey });
    elementsSession = await session.elements({ sdkAuthorization: fetchedData.sdkAuthorization });
    out('elementsOutput', 'Elements session ready.');
  } catch (err) {
    out('elementsOutput', 'Error: ' + err.message);
  }
};

window.mountPaymentElement = () => {
  if (!elementsSession) { out('confirmOutput', 'Call elements() first.'); return; }
  
  // Create PaymentElement with ALL RED configuration
  paymentElement = elementsSession.create({
    type: 'paymentElement',
    options: {
      merchantDisplayName: 'Hyperswitch Store',
      // subscribedEvents: ['FORM_STATUS'],
      appearance: {
        colors: {
          light: {
            primary: '#D32F2F',
            background: '#FAFAFA',
            componentBackground: '#FFFFFF',
            componentBorder: '#E0E0E0',
            componentDivider: '#EEEEEE',
            componentText: '#212121',
            primaryText: '#212121',
            secondaryText: '#757575',
            placeholderText: '#BDBDBD',
            icon: '#757575',
            error: '#D32F2F',
            loaderBackground: '#FFEBEE',
            loaderForeground: '#D32F2F'
          },
          dark: {
            primary: '#EF5350',
            background: '#121212',
            componentBackground: '#2C2C2C',
            componentBorder: '#424242',
            componentDivider: '#333333',
            componentText: '#EEEEEE',
            primaryText: '#EEEEEE',
            secondaryText: '#BDBDBD',
            placeholderText: '#757575',
            icon: '#BDBDBD',
            error: '#EF5350',
            loaderBackground: '#3E2723',
            loaderForeground: '#EF5350'
          }
        },
        shapes: {
          borderRadius: 12,
          borderWidth: 1
        },
        font: {
          scale: 1.0
        },
        primaryButton: {
          shapes: {
            borderRadius: 12
          },
          colors: {
            light: {
              background: '#D32F2F',
              text: '#FFFFFF',
              border: '#D32F2F'
            },
            dark: {
              background: '#EF5350',
              text: '#FFFFFF',
              border: '#EF5350'
            }
          }
        },
        theme: 'Default'
      },
      placeholder: {
        cardNumber: '4242 4242 4242 4242',
        expiryDate: 'MM / YY',
        cvv: 'CVC'
      }
    }
  });
  
  paymentElement.mount('#payment-element');
  
  paymentElement.on('FORM_STATUS', (event) => console.log('eventt:', JSON.stringify(event)));
  paymentElement.on('PAYMENT_METHOD_STATUS', (event) => console.log('eventt2:', JSON.stringify(event)));
  paymentElement.on('PAYMENT_METHOD_INFO_CARD', (event) => console.log('eventt3:', JSON.stringify(event)));
  
  out('confirmOutput', 'PaymentElement mounted with ALL RED configuration');
};

window.unmountPaymentElement = () => {
  paymentElement?.unmount();
  paymentElement = null;
};

window.confirmViaElement = async () => {
  if (!paymentElement) { out('confirmOutput', 'Mount PaymentElement first.'); return; }
  out('confirmOutput', 'Confirming…');
  try {
    const result = await paymentElement.confirmPayment();
    out('confirmOutput', resultText(result));
  } catch (err) {
    out('confirmOutput', 'Error: ' + err.message);
  }
};

window.updateIntent = async () => {
  if (!elementsSession) { out('confirmOutput', 'Call elements() first.'); return; }
  if (!fetchedData?.paymentId) { out('confirmOutput', 'No paymentId available.'); return; }
  out('confirmOutput', 'Updating intent…');
  try {
    const result = await elementsSession.updateIntent(async () => {
      const res = await fetch(`${SERVER_URL}/update-payment`, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ paymentId: fetchedData.paymentId, currency: 'HKD', amount: 2999 }),
      });
      if (!res.ok) throw new Error(`HTTP ${res.status}`);
      return (await res.json()).sdkAuthorization;
    });
    out('confirmOutput', 'updateIntent result: ' + JSON.stringify(result));
  } catch (err) {
    out('confirmOutput', 'Error: ' + err.message);
  }
};
