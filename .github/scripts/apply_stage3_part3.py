#!/usr/bin/env python3
from __future__ import annotations
import pathlib, re
ROOT = pathlib.Path(__file__).resolve().parents[2]

def read(p): return (ROOT/p).read_text(encoding='utf-8')
def write(p,s):
    q=ROOT/p; q.parent.mkdir(parents=True,exist_ok=True); q.write_text(s,encoding='utf-8')
def once(p,old,new):
    s=read(p); c=s.count(old)
    if c!=1: raise RuntimeError(f'{p}: expected 1 occurrence, got {c}: {old[:120]!r}')
    write(p,s.replace(old,new,1))
def regex_once(p,pat,repl,flags=0):
    s=read(p); n,c=re.subn(pat,repl,s,count=1,flags=flags)
    if c!=1: raise RuntimeError(f'{p}: regex expected 1, got {c}: {pat}')
    write(p,n)

# ---------------- Profession catalog/registry deterministic hardening ----------------
p='src/main/java/hu/taliann/icesmp/managers/ProfessionRecipeCatalog.java'
s=read(p)
s=s.replace('import java.util.Map;','import java.util.Map;\nimport java.util.Set;\nimport java.util.TreeSet;\nimport java.util.HashSet;')
s=s.replace('''        for (final String id : root.getKeys(false)) {
            final ConfigurationSection section = root.getConfigurationSection(id);''','''        final Set<String> semanticFingerprints = new HashSet<>();
        for (final String id : new TreeSet<>(root.getKeys(false))) {
            final ConfigurationSection section = root.getConfigurationSection(id);''',1)
s=s.replace('''            byId.put(recipe.id(), recipe);
            byProfession.computeIfAbsent(recipe.profession(), key -> new ArrayList<>()).add(recipe);''','''            if (byId.putIfAbsent(recipe.id(), recipe) != null) {
                throw new IllegalStateException("Duplicate profession recipe id: " + recipe.id());
            }
            final String fingerprint = semanticFingerprint(recipe);
            if (!semanticFingerprints.add(fingerprint)) {
                throw new IllegalStateException("Semantic duplicate profession recipe: " + recipe.id()
                        + " (" + fingerprint + ")");
            }
            byProfession.computeIfAbsent(recipe.profession(), key -> new ArrayList<>()).add(recipe);''',1)
needle='''    /** Minden recept-id betöltési sorrendben (admin item-adó parancs tab-complete-je). */'''
insert=r'''    /** Canonical input/output signature independent of YAML order, profession and progression metadata. */
    public static String semanticFingerprint(final Recipe recipe) {
        final List<String> inputs = new ArrayList<>();
        recipe.ingredients().entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .forEach(entry -> inputs.add("material:" + entry.getKey().name() + ':' + entry.getValue()));
        recipe.uniqueIngredients().entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .forEach(entry -> inputs.add("unique:" + entry.getKey() + ':' + entry.getValue()));
        final String output = recipe.uniqueResult() == null
                ? "material:" + recipe.result().name()
                : "unique:" + recipe.uniqueResult();
        return String.join("+", inputs) + "->" + output + ':' + recipe.resultAmount();
    }

'''
if s.count(needle)!=1: raise RuntimeError('catalog semantic insertion mismatch')
s=s.replace(needle,insert+needle,1)
write(p,s)

p='src/main/java/hu/taliann/icesmp/managers/ProfessionRecipeManager.java'
s=read(p)
s=s.replace('import java.util.Locale;','import java.util.Locale;\nimport java.util.LinkedHashSet;\nimport java.util.Set;')
s=s.replace('''    private final List<Recipe> recipes = new ArrayList<>();''','''    private final List<Recipe> recipes = new ArrayList<>();
    private final Set<NamespacedKey> registeredRecipeKeys = new LinkedHashSet<>();''',1)
s=s.replace('''    /** Builds the recipe set and registers it with the server (call once on enable). */
    public void registerRecipes() {
        if (!isEnabled()) {
            return;
        }
        recipes.clear();''','''    /** Idempotently rebuilds the Bukkit registry; disabled/reload states remove stale keys first. */
    public synchronized void registerRecipes() {
        clearRegisteredRecipes();
        recipes.clear();
        if (!isEnabled()) {
            return;
        }''',1)
s=s.replace('''                final ShapedRecipe shaped = new ShapedRecipe(new NamespacedKey(plugin, "prof_" + recipe.id()), recipe.result());
                shaped.shape(recipe.shape());
                recipe.ingredients().forEach(shaped::setIngredient);
                plugin.getServer().addRecipe(shaped);''','''                final NamespacedKey key = new NamespacedKey(plugin, "prof_" + recipe.id());
                final ShapedRecipe shaped = new ShapedRecipe(key, recipe.result());
                shaped.shape(recipe.shape());
                recipe.ingredients().forEach(shaped::setIngredient);
                if (plugin.getServer().addRecipe(shaped)) {
                    registeredRecipeKeys.add(key);
                } else {
                    plugin.getLogger().warning("Server rejected duplicate profession recipe key: " + key);
                }''',1)
needle='''    /**
     * The requirement for a crafted result, if it is a profession masterwork.'''
insert=r'''    /** Removes only keys owned by this manager; safe on reload and plugin disable. */
    public synchronized void clearRegisteredRecipes() {
        for (final NamespacedKey key : registeredRecipeKeys) {
            plugin.getServer().removeRecipe(key);
        }
        registeredRecipeKeys.clear();
    }

    public void shutdown() {
        clearRegisteredRecipes();
        recipes.clear();
    }

'''
if s.count(needle)!=1: raise RuntimeError('recipe manager cleanup insertion mismatch')
s=s.replace(needle,insert+needle,1)
write(p,s)

p='src/main/resources/config/profession-recipes.yml'
s=read(p)
s,n=re.subn(r'(?ms)^  kezdo_horgaszbot:\n(?:(?!^  [a-z0-9_]+:\n).)*','',s,count=1)
if n!=1: raise RuntimeError(f'kezdo_horgaszbot removal mismatch {n}')
write(p,s)

p='src/main/java/hu/taliann/icesmp/core/IceSMPCore.java'
s=read(p)
old='''            professionRecipeCatalog.load();
            crateManager.reloadConfig();'''
new='''            professionRecipeCatalog.load();
            professionRecipeManager.registerRecipes();
            crateManager.reloadConfig();'''
if s.count(old)!=1: raise RuntimeError('core recipe reload anchor mismatch')
s=s.replace(old,new,1)
old='''        shutdownStep("crateManager", crateManager::shutdown);'''
new='''        shutdownStep("professionRecipeManager", professionRecipeManager::shutdown);
        shutdownStep("crateManager", crateManager::shutdown);'''
if s.count(old)!=1: raise RuntimeError('core recipe shutdown anchor mismatch')
s=s.replace(old,new,1)
old='''            if (key.startsWith("resource-pack.")) {
                resourcePackReloadHook.run();
            }'''
new='''            if (key.startsWith("resource-pack.")) {
                resourcePackReloadHook.run();
            }
            if (key.startsWith("professions.recipes.")) {
                professionRecipeManager.registerRecipes();
            }'''
if s.count(old)!=1: raise RuntimeError('core config hook anchor mismatch')
s=s.replace(old,new,1)
write(p,s)

print('stage3 part 3 applied')
