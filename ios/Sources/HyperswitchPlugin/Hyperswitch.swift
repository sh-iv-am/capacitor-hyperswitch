import Foundation

@objc public class Hyperswitch: NSObject {
    @objc public func echo(_ value: String) -> String {
        print(value)
        return value
    }
}
