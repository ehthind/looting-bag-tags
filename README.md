# Looting Bag Tags

![Looting Bag Tags icon](icon.png)

Creates a Bank Tags tab with a looting bag icon when you deposit items from your
looting bag. It collects loot across trips, even when you already have the
same items elsewhere in your bank.

## How to use

1. Deposit your looting bag into the bank.
2. If the plugin's tab is missing, it creates **looting bag deposits** and adds
   the item types that moved from the bag into the bank.
3. Later looting bag deposits add new item types to the same tab, keeping the
   items from previous trips.
4. Delete the tab after selling your loot. The next bag deposit creates a fresh
   tab, even if you left the previous tab's items tagged when deleting it.

If the default name already belongs to another tag, the plugin chooses a
numbered name. Your other tabs and tags remain intact.

**Quantities are your whole bank stacks.** Withdraw-All takes everything you
own of an item, including stock you had before the trip. Use Withdraw-X to keep
existing stock.

Partial deposits include only item types that successfully enter the bank.
Unknown bag contents are not guessed; open the bag's contents at the bank before
depositing if needed. Ordinary inventory deposits are excluded. The plugin
observes deposits and updates Bank Tags; it does not perform game actions.

## Installation

Once this plugin is approved and available in RuneLite's Plugin Hub, search for
**Looting Bag Tags** there and install it. It uses RuneLite's built-in **Bank
Tags** plugin. Until approval, it is available for development from source.

## Development

Use JDK 11 to build and run the plugin:

```sh
./gradlew test
./gradlew run
```

For a Jagex account, follow RuneLite's official
[Using Jagex Accounts guide](https://github.com/runelite/runelite/wiki/Using-Jagex-Accounts)
to sign in to the development client.

When testing in game, check the first bag deposit, new item types added on later
trips, and recreating a deleted tab. Include items already in the bank and
confirm ordinary inventory deposits and unrelated tabs are unaffected.

## License

[BSD 2-Clause](LICENSE). Copyright 2026 Amrit Thind.
