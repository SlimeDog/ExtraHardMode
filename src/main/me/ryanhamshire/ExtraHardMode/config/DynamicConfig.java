package me.ryanhamshire.ExtraHardMode.config;

import com.google.common.collect.HashBasedTable;
import com.google.common.collect.Table;
import me.ryanhamshire.ExtraHardMode.ExtraHardMode;
import me.ryanhamshire.ExtraHardMode.service.ConfigNode;
import me.ryanhamshire.ExtraHardMode.service.EHMModule;
import org.bukkit.World;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.FilenameFilter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;


public class DynamicConfig extends EHMModule
{
    /**Guava Table: When getting a value for a specific world we go through the rows and the later always override the before,
     * in every column there is a ConfigNode for the worlds it applies to**/
    private Table <Integer, ConfigNode, Object> multiConfig;

    public DynamicConfig (ExtraHardMode plugin)
    {
        super(plugin);
    }

    @Override
    public void starting()
    {
        initTable();
        validateAndLoad(getAllConfigs(plugin.getDataFolder()));
        //rewriteConfig(plugin.getDataFolder() + File.separator + "config.yml");
    }

    @Override
    public void closing()
    {
        //Kill complex objects
        multiConfig = null;
    }

    /**
     * Initialize/clear the central Table for storing all configvalues
     */
    public void initTable ()
    {
        multiConfig = HashBasedTable.create();
    }

    /**
     * Get/Return all worlds where ehm or parts of ehm are activated
     * @return
     */
    public ArrayList<String> getEnabledWorlds()
    {
        ArrayList<String> worlds = new ArrayList<String>();
        return worlds;
    }

    /**
     * Search the base directory for config files, check the files to be valid
     * @param baseDir
     * @return all validated FileConfigurations found in the plugin directory
     */
    public ArrayList<FileConfiguration> getAllConfigs (File baseDir)
    {
        //All Filenames that are .yml files
        String [] filePaths = baseDir.list(new FilenameFilter()
        {
            @Override
            public boolean accept(File dir, String name)
            {
                //hardcoded config.yml path...
                return name.endsWith(".yml") &! name.equals("config.yml");
            }
        });

        //The main FileConfiguration is loaded first, after that we load the FileConfigurations (Overrides) for other worlds
        ArrayList <FileConfiguration> configFiles = new ArrayList<FileConfiguration>();
        configFiles.add(plugin.getConfig());
        for (String file : filePaths)
        {
            FileConfiguration config = YamlConfiguration.loadConfiguration(new File(baseDir + File.separator + file)); //shouldnt cause an error
            configFiles.add(config);
        }

        //Check if the config is a valid config.
        Iterator iter = configFiles.iterator();
        while (iter.hasNext())
        {
            FileConfiguration config = (FileConfiguration) iter.next();
            //If it doesn't equal the type it might be some other config, like messages.yml
            if (config.isSet(RootNode.TYPE.getPath()) && config.get(RootNode.TYPE.getPath()).equals(RootNode.TYPE.getDefaultValue())
                    //the world attribute has to be set aswell
                    && config.isSet(RootNode.WORLDS.getPath()) && config.getStringList(RootNode.WORLDS.getPath()) != null);
            else iter.remove();

        }
        return configFiles;
    }

    /**
     *
     * @param configFiles
     */
    public void validateAndLoad (ArrayList<FileConfiguration> configFiles)
    {
        //Lexically load the FileConfigurations into memory, first always being config.yml
        for (int rowNum = 0; rowNum < configFiles.size(); rowNum++)
        {
            FileConfiguration config = configFiles.get(rowNum);
            //Iterate over the RootNodes and look if we can find those paths in the FileConfiguration
            for (RootNode node : RootNode.values())
            {
                if (config.contains(node.getPath()))
                {
                    Object valueFromFile;
                    //First config contains defaults. If an option is not present, other configs don't query the defaults and only contain overrides
                    if (rowNum == 0)
                    {
                        valueFromFile = loadFromConfig(config, node, true /*return defaults if not present*/);
                    }else
                    {
                        valueFromFile = loadFromConfig(config, node, false);
                    }
                    //Validate Integers based on their SubType
                    if (node.getSubType() != null && node.getVarType().equals(ConfigNode.VarType.INTEGER))
                    {
                        int val = (Integer) valueFromFile;
                        switch (node.getSubType())
                        {
                            case PERCENTAGE:
                            {
                                valueFromFile = validatePercentage(node, val);
                                break;
                            }
                            case Y_VALUE:
                            {
                                valueFromFile = validateYCoordinate(node, config.getStringList(RootNode.WORLDS.getPath()), val);
                                break;
                            }
                            case HEALTH:
                            {
                                valueFromFile = validateCustom(node, 1, 20, val);
                                break;
                            }
                            default:
                                throw new UnsupportedOperationException("SubType of " + node.getPath() + " hasn't got a validate method");
                        }
                    }
                    updateOption(rowNum, node, valueFromFile);
                }
            }
        }
    }

    /**
     * Load the ConfigNode from the FileConfiguration passed in.
     * @param config   -  FileConfiguration to load from
     * @param node     -  ConfigNode for the Path and DefaultValue
     * @param defaults -  if true wil return the default value if not found in config
     * @return the Object matching the type of the ConfigNode, otherwise null if not found
     */
    public Object loadFromConfig (FileConfiguration config, ConfigNode node, boolean defaults)
    {
        Object obj;
        switch (node.getVarType())
        {
            case LIST:
            {
                obj = config.getStringList(node.getPath());
                break;
            }
            case DOUBLE:
            {
                obj =  config.getDouble(node.getPath());
                break;
            }
            case STRING:
            {
                obj = config.getString(node.getPath());
                break;
            }
            case INTEGER:
            {
                obj = config.getInt(node.getPath());
                break;
            }
            case BOOLEAN:
            {
                obj = config.getBoolean(node.getPath());
                break;
            }
            default:
            {
                obj = config.get(node.getPath());
                throw new UnsupportedOperationException(node.getPath() + "No specific getter availible for Type: " + " " + node.getVarType());
            }
        }
        if (obj.equals(null) && defaults)
        {
            obj = node.getDefaultValue();
        }
        return obj;
    }

    /**
     * Rewrite the config file to disk.
     * @param fileName
     */
    public void rewriteConfig(String fileName)
    {
        FileConfiguration config = new YamlConfiguration();
        RootNode[] nodes = RootNode.values();
        for (RootNode node : nodes)
        {
            switch (node.getVarType())
            {
                case BOOLEAN:
                {
                    config.set(node.getPath(), getBooleanById(node, 0));
                    break;
                }
                case STRING:
                {
                    config.set(node.getPath(), getStringById(node, 0));
                    break;
                }
                case INTEGER:
                {
                    config.set(node.getPath(), getIntById(node, 0));
                    break;
                }
                case DOUBLE:
                {
                    config.set(node.getPath(), getDoubleById(node, 0));
                    break;
                }
                case LIST:
                {
                    config.set(node.getPath(), getStringListById(node, 0));
                    break;
                }
                default:
                {
                    break;
                }
            }
        }
        try //dunno how to get the file for the cfg to overwrite...
        {
            config.save(new File(fileName));
        } catch (IOException e)
        {
            e.printStackTrace();
        }
    }

    /**
     * Save an option to memory
     *
     * @param node - ConfigNode to update.
     */
    public void updateOption(int id, final ConfigNode node, Object value)
    {
        if (multiConfig != null) multiConfig.put(id, node, value);
        else throw new NullPointerException("NPE: multiConfig == null");
    }

    /**
     * Get the integer value of the node for the specific world
     *
     * @param node - Node to use.
     * @param world - World for which tho get config
     * @return Value of the node. Returns -1 if unknown.
     */
    public int getInt(final ConfigNode node, String world)
    {
        return getIntById(node, getLastIndex(node, world));
    }

    /**
     * Get the int value of the node by the index in the Table
     *
     * @param node - Node to use
     * @param index - in the Table to get the value from
     *
     */
    public int getIntById(final ConfigNode node, int index)
    {
        int i = -1;
        switch (node.getVarType())
        {
            case INTEGER:
            {
                try
                {
                    i = ((Integer) multiConfig.get(index, node)).intValue();
                } catch (NullPointerException npe)
                {
                    i = ((Integer) node.getDefaultValue()).intValue();
                }
                break;
            }
            default:
            {
                throw new IllegalArgumentException("Attempted to get " + node.toString() + " of type " + node.getVarType() + " as an integer.");
            }
        }
        return i;
    }

    /**
     * Get the string value of the node.
     *
     * @param node - Node to use.
     * @return Value of the node. Returns and empty string if unknown.
     */
    public String getString(final ConfigNode node, String world)
    {
        return getStringById(node, getLastIndex(node, world));
    }

    public String getStringById (ConfigNode node, int index)
    {
        String out = "";
        switch (node.getVarType())
        {
            case STRING:
            {
                try
                {
                    out = (String) multiConfig.get(index, node);
                    if (out == null)
                    {
                        out = (String) node.getDefaultValue();
                    }
                } catch (NullPointerException e)
                {
                    out = (String) node.getDefaultValue();
                }
                break;
            }
            default:
            {
                throw new IllegalArgumentException("Attempted to get " + node.toString() + " of type " + node.getVarType() + " as a string.");
            }
        }
        return out;
    }

    /**
     * Get the list value of the node.
     *
     * @param node - Node to use.
     * @return Value of the node. Returns an empty list if unknown.
     */
    public List<String> getStringList(final ConfigNode node, String world)
    {
        return getStringListById(node, getLastIndex(node, world));
    }

    /**
     * Get the list value of the node.
     *
     * @param node - Node to use.
     * @return Value of the node. Returns an empty list if unknown.
     */
    @SuppressWarnings("unchecked")
    public List<String> getStringListById(final ConfigNode node, int index)
    {
        List<String> list = new ArrayList<String>();
        switch (node.getVarType())
        {
            case LIST:
            {
                try
                {
                    list = (List<String>) multiConfig.get(index, node);
                    if (list == null)
                    {
                        list = (List<String>) node.getDefaultValue();
                    }
                } catch (NullPointerException e)
                {
                    list = (List<String>) node.getDefaultValue();
                }
                break;
            }
            default:
            {
                throw new IllegalArgumentException("Attempted to get " + node.toString() + " of type " + node.getVarType() + " as a List<String>.");
            }
        }
        return list;
    }

    /**
     * Get the double value of the node.
     *
     * @param node - Node to use.
     * @return Value of the node. Returns 0 if unknown.
     */
    public double getDouble(final ConfigNode node, String world)
    {
        return getDoubleById(node, getLastIndex(node, world));
    }

    /**
     * Get the double value of the node.
     *
     * @param node - Node to use.
     * @return Value of the node. Returns 0 if unknown.
     */
    public double getDoubleById(final ConfigNode node, int index)
    {
        double d = 0.0;
        switch (node.getVarType())
        {
            case DOUBLE:
            {
                try
                {
                    d = ((Double) multiConfig.get(index, node)).doubleValue();
                } catch (NullPointerException npe)
                {
                    d = ((Double) node.getDefaultValue()).doubleValue();
                }
                break;
            }
            default:
            {
                throw new IllegalArgumentException("Attempted to get " + node.toString() + " of type " + node.getVarType() + " as a double.");
            }
        }
        return d;
    }

    /**
     * Get the boolean value of the node.
     *
     * @param node - Node to use.
     * @param world - where this config applies
     * @return Value of the node. Returns false if unknown.
     */
    public boolean getBoolean(ConfigNode node, String world)
    {
        return getBooleanById(node, getLastIndex(node, world));
    }

    /**
     * Get the boolean value of the node.
     *
     * @param node - Node to use.
     * @param index - to get
     * @return Value of the node. Returns false if unknown.
     */
    public boolean getBooleanById(ConfigNode node, int index)
    {
        boolean b = false;
        switch (node.getVarType())
        {
            case BOOLEAN:
            {
                try
                {
                    b = ((Boolean) multiConfig.get(index, node)).booleanValue();
                } catch (NullPointerException e)
                {
                    b = (Boolean) node.getDefaultValue();
                }
                break;
            }
            default:
            {
                throw new IllegalArgumentException("Attempted to get " + node.toString() + " of type " + node.getVarType() + " as a boolean.");
            }
        }
        return b;
    }

    /**
     * Get the highest index for the given RootNode and World: Higher Indexes overwrite previous indexes.
     * only public for testing purposes
     */
    public int getLastIndex (ConfigNode node, String worldName)
    {
        //0 = default config which requires all Nodes to be present
        int highestIndex = 0;
        //Every row represents a FileConfiguration
        int rowSize = multiConfig.rowMap().size();
        //Cache
        String path = node.getPath();

        for (int i = 0; i < rowSize; i++)
        {
            Map<ConfigNode, Object> map = multiConfig.rowMap().get(i);

            if (map.containsKey(node) && map.containsKey(RootNode.WORLDS))
            {
                ArrayList <String> worlds = (ArrayList <String>) map.get(RootNode.WORLDS);
                if (worlds.contains(worldName))
                    highestIndex = i;
            }
        }
        return highestIndex;
    }

    /**
     * Validate Y coordinate limit for the given configuration option against the
     * list of enabled worlds.
     *
     * @param node   - Root node to validate.
     * @param worlds - List of worlds to check against.
     * @param value  - Integer to validate
     *
     * @return either the original value or adjusted if out of bounds
     */
    private int validateYCoordinate(RootNode node, List<String> worlds, Integer value)
    {
        //Either 255 or the height of the first world loaded is the default max height
        int maxHeight = plugin.getServer().getWorlds().size() > 0 ? plugin.getServer().getWorlds().get(0).getMaxHeight() : 255;
        if (value < 0)
        {
            plugin.getLogger().warning(plugin.getTag() + " Y coordinate for " + node.getPath() + " cannot be less than 0.");
            value = 0;
        }
        for (String worldName : worlds)
        {
            World world = plugin.getServer().getWorld(worldName);
            //if world is not loaded (yet)
            if (world != null) maxHeight = world.getMaxHeight();
            if (value > maxHeight)
            {
                plugin.getLogger().warning(
                        plugin.getTag() + " Y coordinate for " + node.getPath() + " is greater than the max height for world " + worldName);
                value = maxHeight;
            }
        }
        return value;
    }

    /**
     * Validate percentage (0-100) value for given configuration option.
     *
     * @param node - Root node to validate.
     * @param value  - Integer to validate
     *
     * @return either the original value or adjusted if out of bounds
     */
    private int validatePercentage(RootNode node, Integer value)
    {
        if (value < 0)
        {
            plugin.getLogger().warning(plugin.getTag() + " Percentage for " + node.getPath() + " cannot be less than 0.");
            value = 0;
        }
        else if (value > 100)
        {
            plugin.getLogger().warning(plugin.getTag() + " Percentage for " + node.getPath() + " cannot be greater than 100.");
            value = 100;
        }
        return value;
    }

    /**
     * Validates a configOption with custom bounds
     * @param node the configNode
     * @param minVal the minimum value the config is allowed to have
     * @param maxVal the maximum value for the config
     * @param value  - Integer to validate
     *
     * @return either the original value or adjusted if out of bounds
     */
    private int validateCustom (RootNode node, int minVal, int maxVal, Integer value)
    {
        if (value < minVal)
        {
            plugin.getLogger().warning(plugin.getTag() + " Value for " + node.getPath() + "cannot be smaller than 0.");
            value = 0;
        }
        else if (value > maxVal)
        {
            plugin.getLogger().warning(plugin.getTag() + " Value for " + node.getPath() + "cannot be greater than " + maxVal);
            value = maxVal;
        }
        return value;
    }
}